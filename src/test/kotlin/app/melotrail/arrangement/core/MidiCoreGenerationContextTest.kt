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
import app.melotrail.project.MidiCoreArrangementPlan
import app.melotrail.project.MidiCoreArrangementPurpose
import app.melotrail.project.MidiCoreAuthorityHasher
import app.melotrail.project.MidiCoreBoundaryIntent
import app.melotrail.project.MidiCoreGeneratorInput
import app.melotrail.project.MidiCoreGrooveDrive
import app.melotrail.project.MidiCoreGrooveFeel
import app.melotrail.project.MidiCoreGrooveSubdivision
import app.melotrail.project.MidiCoreOccurrenceArrangementPlan
import app.melotrail.project.MidiCoreProject
import app.melotrail.project.MidiCoreRegisterPreference
import app.melotrail.project.MidiCoreRoleActivity
import app.melotrail.project.MidiCoreRolePlanSettings
import app.melotrail.project.MidiCoreSharedGrooveIntent
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
    fun `explicit piano boundary changes the chords context fingerprint without reading accepted state`() {
        val sourceProject = project()
        val snapshot = MidiCoreAuthoritySnapshot.from(sourceProject)
        val profile = MidiCorePerformanceProfileCatalog.requireForRole(CandidateRole.CHORDS, "chords.sustained")
        val plain = MidiCoreGenerationContext.forOccurrence(
            snapshot,
            CandidateRole.CHORDS,
            "chorus-1",
            profile,
            MidiCoreChordRhythmPatternId.SUSTAINED.id,
            MidiCoreGeneratorInput("midi-core", "chords-v4", MidiCoreChordRhythmPatternId.SUSTAINED.id, 7),
        )
        val boundary = MidiCorePianoVoicingBoundarySummary(
            "verse-1",
            1_920,
            listOf(48, 52, 55),
            snapshot.fingerprint.scopeHash("verse-1", CandidateRole.CHORDS),
        )
        val continued = plain.copy(pianoVoicingBoundary = boundary)

        assertNotEquals(plain.contextSha256, continued.contextSha256)
        assertNotEquals(plain.generationFingerprint.sha256, continued.generationFingerprint.sha256)
        assertEquals(boundary.sha256, continued.generationFingerprint.boundarySummarySha256)
        assertEquals(emptyList(), continued.acceptedDependencies)
    }

    @Test
    fun `confirmed plan resolves scoped groove neighbor and repeat inputs deterministically`() {
        val planned = project().copy(arrangementPlan = repeatPlan())
        val first = MidiCoreGenerationContext.from(
            planned,
            CandidateRole.BASS,
            "verse-1",
            MidiCorePerformanceProfileCatalog.requireForRole(CandidateRole.BASS, "bass.muted-plucked"),
            MidiCoreBassPatternId.ROOT_FIFTH.id,
            MidiCoreGeneratorInput("midi-core", "bass-v1", MidiCoreBassPatternId.ROOT_FIFTH.id, 7),
        )
        val repeated = MidiCoreGenerationContext.from(
            planned,
            CandidateRole.BASS,
            "chorus-1",
            MidiCorePerformanceProfileCatalog.requireForRole(CandidateRole.BASS, "bass.muted-plucked"),
            MidiCoreBassPatternId.ROOT_FIFTH.id,
            MidiCoreGeneratorInput("midi-core", "bass-v1", MidiCoreBassPatternId.ROOT_FIFTH.id, 7),
        )
        val changedUnconsumedRole = planned.copy(arrangementPlan = repeatPlan(chordsDensity = 99))
        val unchanged = MidiCoreGenerationContext.from(
            changedUnconsumedRole,
            CandidateRole.BASS,
            "chorus-1",
            MidiCorePerformanceProfileCatalog.requireForRole(CandidateRole.BASS, "bass.muted-plucked"),
            MidiCoreBassPatternId.ROOT_FIFTH.id,
            MidiCoreGeneratorInput("midi-core", "bass-v1", MidiCoreBassPatternId.ROOT_FIFTH.id, 7),
        )
        val changedRepeatSource = MidiCoreGenerationContext.from(
            planned.copy(arrangementPlan = repeatPlan(bassDensity = 49)),
            CandidateRole.BASS,
            "chorus-1",
            MidiCorePerformanceProfileCatalog.requireForRole(CandidateRole.BASS, "bass.muted-plucked"),
            MidiCoreBassPatternId.ROOT_FIFTH.id,
            MidiCoreGeneratorInput("midi-core", "bass-v1", MidiCoreBassPatternId.ROOT_FIFTH.id, 7),
        )
        val changedGroove = MidiCoreGenerationContext.from(
            planned.copy(arrangementPlan = repeatPlan(feel = MidiCoreGrooveFeel.HALF_TIME)),
            CandidateRole.BASS,
            "chorus-1",
            MidiCorePerformanceProfileCatalog.requireForRole(CandidateRole.BASS, "bass.muted-plucked"),
            MidiCoreBassPatternId.ROOT_FIFTH.id,
            MidiCoreGeneratorInput("midi-core", "bass-v1", MidiCoreBassPatternId.ROOT_FIFTH.id, 7),
        )

        assertEquals(MidiCoreGrooveFeel.SWING, first.occurrencePlan?.sharedGroove?.feel)
        assertEquals("chorus-1", first.occurrencePlan?.nextNeighbor?.occurrenceId)
        assertEquals("verse-1", repeated.occurrencePlan?.previousNeighbor?.occurrenceId)
        assertEquals("verse-1", repeated.occurrencePlan?.repeatSource?.occurrenceId)
        assertEquals(61, repeated.occurrencePlan?.current?.energy)
        assertEquals(48, repeated.occurrencePlan?.current?.roleSettings?.density)
        assertEquals(MidiCoreSectionPolicy(), repeated.sectionPolicy)
        assertEquals(repeated.contextSha256, MidiCoreGenerationContext.from(
            planned,
            CandidateRole.BASS,
            "chorus-1",
            MidiCorePerformanceProfileCatalog.requireForRole(CandidateRole.BASS, "bass.muted-plucked"),
            MidiCoreBassPatternId.ROOT_FIFTH.id,
            MidiCoreGeneratorInput("midi-core", "bass-v1", MidiCoreBassPatternId.ROOT_FIFTH.id, 7),
        ).contextSha256)
        assertEquals(repeated.generationFingerprint.sha256, unchanged.generationFingerprint.sha256)
        assertNotEquals(repeated.generationFingerprint.sha256, changedRepeatSource.generationFingerprint.sha256)
        assertNotEquals(repeated.generationFingerprint.sha256, changedGroove.generationFingerprint.sha256)
        assertFailsWith<IllegalArgumentException> {
            repeatPlan().copy(
                occurrences = repeatPlan().occurrences.map { it.copy(repeatOrdinal = 1) },
            )
        }
    }

    @Test
    fun `an unrelated plan edit preserves a reusable scoped piano boundary`() {
        val planned = fourOccurrencePlannedProject()
        val repeated = MidiCoreGenerationContext.from(
            planned,
            CandidateRole.BASS,
            "part-3",
            MidiCorePerformanceProfileCatalog.requireForRole(CandidateRole.BASS, "bass.muted-plucked"),
            MidiCoreBassPatternId.ROOT_FIFTH.id,
            MidiCoreGeneratorInput("midi-core", "bass-v1", MidiCoreBassPatternId.ROOT_FIFTH.id, 17),
        )
        assertEquals("part-2", repeated.occurrencePlan?.previousNeighbor?.occurrenceId)
        assertEquals("part-4", repeated.occurrencePlan?.nextNeighbor?.occurrenceId)
        assertEquals("part-1", repeated.occurrencePlan?.repeatSource?.occurrenceId)
        val firstContext = MidiCoreGenerationContext.from(
            planned,
            CandidateRole.CHORDS,
            "part-1",
            MidiCorePerformanceProfileCatalog.requireForRole(CandidateRole.CHORDS, "chords.sustained"),
            MidiCoreChordRhythmPatternId.SUSTAINED.id,
            MidiCoreGeneratorInput("midi-core", "chords-v5", MidiCoreChordRhythmPatternId.SUSTAINED.id, 17),
        )
        val boundary = requireNotNull(MidiCoreChordGenerator.generate(firstContext).outgoingPianoVoicingBoundary)
        assertEquals(null, firstContext.occurrencePlan?.sharedGroove)
        val originalPlan = requireNotNull(planned.arrangementPlan)
        val changedPlan = originalPlan.copy(
            occurrences = originalPlan.occurrences.map { occurrence ->
                if (occurrence.occurrenceId == "part-4") occurrence.copy(energy = occurrence.energy + 1) else occurrence
            },
        )
        val changed = planned.copy(arrangementPlan = changedPlan)
        val beforeAuthority = MidiCoreAuthoritySnapshot.from(planned)
        val afterAuthority = MidiCoreAuthoritySnapshot.from(changed)

        val continued = MidiCoreGenerationContext.from(
            changed,
            CandidateRole.CHORDS,
            "part-2",
            MidiCorePerformanceProfileCatalog.requireForRole(CandidateRole.CHORDS, "chords.sustained"),
            MidiCoreChordRhythmPatternId.SUSTAINED.id,
            MidiCoreGeneratorInput("midi-core", "chords-v5", MidiCoreChordRhythmPatternId.SUSTAINED.id, 17),
            pianoVoicingBoundary = boundary,
        )

        assertNotEquals(beforeAuthority.authorityHash, afterAuthority.authorityHash)
        assertEquals(
            beforeAuthority.fingerprint.scopeHash("part-1", CandidateRole.CHORDS),
            afterAuthority.fingerprint.scopeHash("part-1", CandidateRole.CHORDS),
        )
        assertEquals(afterAuthority.fingerprint.scopeHash("part-1", CandidateRole.CHORDS), boundary.authorityHash)
        assertEquals(boundary, continued.pianoVoicingBoundary)
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

    private fun repeatPlan(
        chordsDensity: Int = 55,
        bassDensity: Int = 48,
        feel: MidiCoreGrooveFeel = MidiCoreGrooveFeel.SWING,
    ) = MidiCoreArrangementPlan(
        MidiCoreArrangementPlan.VERSION,
        MidiCoreSharedGrooveIntent(feel, MidiCoreGrooveSubdivision.EIGHTH, MidiCoreGrooveDrive.STEADY),
        listOf(
            plannedOccurrence("verse-1", 42, chordsDensity, bassDensity, 1),
            plannedOccurrence("chorus-1", 61, 64, 48, 2),
        ),
    )

    private fun fourOccurrencePlannedProject(): MidiCoreProject {
        val occurrences = (1..4).map { index ->
            ProjectSectionOccurrence("part-$index", "verse", "Part $index", (index - 1) * 1_920L, index * 1_920L)
        }
        val authority = requireNotNull(project().authority).copy(
            occurrences = occurrences,
            chordEvents = occurrences.mapIndexed { index, occurrence ->
                AuthoritativeChordEvent("part-${index + 1}-chord", occurrence.id, "C", occurrence.startTick, occurrence.endTick)
            },
            arrangementEndTick = occurrences.last().endTick,
        )
        val plan = MidiCoreArrangementPlan(
            MidiCoreArrangementPlan.VERSION,
            MidiCoreSharedGrooveIntent(MidiCoreGrooveFeel.STRAIGHT, MidiCoreGrooveSubdivision.EIGHTH, MidiCoreGrooveDrive.STEADY),
            listOf(
                plannedOccurrence("part-1", 40, 50, 45, 1, "family-a"),
                plannedOccurrence("part-2", 50, 55, 50, 1, "family-b"),
                plannedOccurrence("part-3", 60, 60, 55, 2, "family-a"),
                plannedOccurrence("part-4", 35, 45, 40, 1, "family-d"),
            ),
        )
        return project().copy(authority = authority, arrangementPlan = plan)
    }

    private fun plannedOccurrence(
        id: String,
        energy: Int,
        chordsDensity: Int,
        bassDensity: Int,
        ordinal: Int,
        repeatFamilyId: String = "repeat-family",
    ) = MidiCoreOccurrenceArrangementPlan(
        id,
        MidiCoreArrangementPurpose.VERSE,
        "phrase-repeat",
        repeatFamilyId,
        ordinal,
        energy,
        listOf(
            MidiCoreRolePlanSettings(CandidateRole.CHORDS, MidiCoreRoleActivity.SUPPORTING, chordsDensity, MidiCoreRegisterPreference.MID),
            MidiCoreRolePlanSettings(CandidateRole.BASS, MidiCoreRoleActivity.SUPPORTING, bassDensity, MidiCoreRegisterPreference.LOW),
            MidiCoreRolePlanSettings(CandidateRole.DRUMS, MidiCoreRoleActivity.SPARSE, 30, MidiCoreRegisterPreference.OPEN),
        ),
        MidiCoreBoundaryIntent.NONE,
        MidiCoreBoundaryIntent.HOLD,
    )
}
