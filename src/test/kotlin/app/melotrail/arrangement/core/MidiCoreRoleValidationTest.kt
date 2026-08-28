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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class MidiCoreRoleValidationTest {
    @Test
    fun `valid chords bass and drums candidates pass their role policies`() {
        val chromaticChords = MidiCoreRoleValidator.validate(
            context(CandidateRole.CHORDS, chordSymbol = "Db"),
            listOf(MidiCoreCandidateEvent.Note(0, 1_920, 61, 80)),
        )
        val bass = MidiCoreRoleValidator.validate(
            context(CandidateRole.BASS),
            listOf(MidiCoreCandidateEvent.Note(0, 480, 36, 80)),
        )
        val drums = MidiCoreRoleValidator.validate(
            context(CandidateRole.DRUMS),
            listOf(
                MidiCoreCandidateEvent.Note(0, 120, 36, 100),
                MidiCoreCandidateEvent.Note(240, 360, 42, 72),
                MidiCoreCandidateEvent.Note(480, 600, 38, 92),
                MidiCoreCandidateEvent.Note(720, 840, 46, 84),
            ),
        )

        assertAccepted(chromaticChords)
        assertAccepted(bass)
        assertAccepted(drums)
    }

    @Test
    fun `common candidate policies return typed rejection findings`() {
        val base = context(CandidateRole.CHORDS)
        val cases = listOf(
            MidiCoreRoleFindingCode.ROLE_MISMATCH to MidiCoreRoleCandidate(
                CandidateRole.BASS, base.occurrence.id, 1, listOf(MidiCoreCandidateEvent.Note(0, 480, 36, 80)),
            ),
            MidiCoreRoleFindingCode.OCCURRENCE_MISMATCH to MidiCoreRoleCandidate(
                CandidateRole.CHORDS, "chorus-1", 1, listOf(MidiCoreCandidateEvent.Note(0, 480, 60, 80)),
            ),
            MidiCoreRoleFindingCode.WRONG_CHANNEL to MidiCoreRoleCandidate(
                CandidateRole.CHORDS, base.occurrence.id, 0, listOf(MidiCoreCandidateEvent.Note(0, 480, 60, 80)),
            ),
            MidiCoreRoleFindingCode.UNSUPPORTED_EVENT to MidiCoreRoleCandidate(
                CandidateRole.CHORDS, base.occurrence.id, 1, listOf(MidiCoreCandidateEvent.Unsupported("cc64", 0)),
            ),
            MidiCoreRoleFindingCode.INVALID_TIMING to MidiCoreRoleCandidate(
                CandidateRole.CHORDS, base.occurrence.id, 1, listOf(MidiCoreCandidateEvent.Note(-1, 120, 60, 80)),
            ),
            MidiCoreRoleFindingCode.NON_POSITIVE_DURATION to MidiCoreRoleCandidate(
                CandidateRole.CHORDS, base.occurrence.id, 1, listOf(MidiCoreCandidateEvent.Note(480, 480, 60, 80)),
            ),
            MidiCoreRoleFindingCode.OUTSIDE_OCCURRENCE to MidiCoreRoleCandidate(
                CandidateRole.CHORDS, base.occurrence.id, 1, listOf(MidiCoreCandidateEvent.Note(0, 1_921, 60, 80)),
            ),
            MidiCoreRoleFindingCode.UNREPRESENTABLE_TICK to MidiCoreRoleCandidate(
                CandidateRole.CHORDS, base.occurrence.id, 1, listOf(MidiCoreCandidateEvent.Note(1, 120, 60, 80)),
            ),
            MidiCoreRoleFindingCode.OUTSIDE_REGISTER to MidiCoreRoleCandidate(
                CandidateRole.CHORDS, base.occurrence.id, 1, listOf(MidiCoreCandidateEvent.Note(0, 120, 40, 80)),
            ),
            MidiCoreRoleFindingCode.INVALID_VELOCITY to MidiCoreRoleCandidate(
                CandidateRole.CHORDS, base.occurrence.id, 1, listOf(MidiCoreCandidateEvent.Note(0, 120, 60, 0)),
            ),
        )

        cases.forEach { (code, candidate) ->
            assertRejected(MidiCoreRoleValidator.validate(base, candidate), code)
        }
    }

    @Test
    fun `chords and bass use the exact authoritative harmony including chromatic chords`() {
        val chordMismatch = MidiCoreRoleValidator.validate(
            context(CandidateRole.CHORDS),
            listOf(MidiCoreCandidateEvent.Note(0, 480, 62, 80)),
        )
        val bassMismatch = MidiCoreRoleValidator.validate(
            context(CandidateRole.BASS),
            listOf(MidiCoreCandidateEvent.Note(0, 480, 39, 80)),
        )

        assertRejected(chordMismatch, MidiCoreRoleFindingCode.HARMONY_MISMATCH)
        assertRejected(bassMismatch, MidiCoreRoleFindingCode.HARMONY_MISMATCH)
        assertAccepted(
            MidiCoreRoleValidator.validate(
                context(CandidateRole.CHORDS, chordSymbol = "Db"),
                listOf(MidiCoreCandidateEvent.Note(0, 480, 61, 80)),
            ),
        )
    }

    @Test
    fun `protected anchor collision blocks while close non-anchor collision remains advisory`() {
        val anchorCollision = MidiCoreRoleValidator.validate(
            context(
                CandidateRole.DRUMS,
                protectedMelodyNotes = listOf(protectedNote(pitch = 36, anchor = true)),
            ),
            listOf(MidiCoreCandidateEvent.Note(0, 120, 36, 80)),
        )
        val nonAnchorCollision = MidiCoreRoleValidator.validate(
            context(
                CandidateRole.DRUMS,
                protectedMelodyNotes = listOf(protectedNote(pitch = 38, anchor = false)),
            ),
            listOf(MidiCoreCandidateEvent.Note(0, 120, 36, 80)),
        )

        assertRejected(anchorCollision, MidiCoreRoleFindingCode.PROTECTED_ANCHOR_COLLISION)
        val advisory = assertAccepted(nonAnchorCollision)
        assertTrue(advisory.findings.any { it.code == MidiCoreRoleFindingCode.MELODY_COLLISION })
        assertTrue(advisory.findings.all { it.severity == MidiCoreRoleFindingSeverity.ADVISORY })
    }

    @Test
    fun `scoped ensemble validation preserves melody space and blocks crowded chord bass register`() {
        val melodyPressure = MidiCoreRoleValidator.validate(
            context(
                CandidateRole.CHORDS,
                protectedMelodyNotes = listOf(protectedNote(pitch = 64, anchor = false)),
            ),
            listOf(MidiCoreCandidateEvent.Note(0, 480, 60, 80)),
        )
        val chordBassConflict = MidiCoreRoleValidator.validate(
            context(
                CandidateRole.CHORDS,
                acceptedDependencies = listOf(
                    dependency(CandidateRole.BASS, listOf(MidiCoreGenerationNote(0, 480, 56, 80))),
                ),
            ),
            listOf(MidiCoreCandidateEvent.Note(0, 480, 60, 80)),
        )

        val advisory = assertAccepted(melodyPressure)
        assertTrue(advisory.findings.any { it.code == MidiCoreRoleFindingCode.MELODY_REGISTER_PRESSURE })
        assertRejected(chordBassConflict, MidiCoreRoleFindingCode.CHORD_BASS_SPACE_CONFLICT)
    }

    @Test
    fun `scoped drum bass intent is advisory while excessive aggregate onset density blocks`() {
        val bass = dependency(CandidateRole.BASS, listOf(MidiCoreGenerationNote(720, 840, 36, 80)))
        val missingKick = MidiCoreRoleValidator.validate(
            context(CandidateRole.DRUMS, acceptedDependencies = listOf(bass)),
            listOf(
                MidiCoreCandidateEvent.Note(0, 120, 36, 90),
                MidiCoreCandidateEvent.Note(0, 120, 42, 70),
                MidiCoreCandidateEvent.Note(480, 600, 38, 88),
            ),
        )
        val crowdedDependencies = listOf(
            dependency(CandidateRole.CHORDS, (0 until 6).map { MidiCoreGenerationNote(0, 480, 60 + it, 80) }),
            dependency(CandidateRole.BASS, (0 until 3).map { MidiCoreGenerationNote(0, 480, 36 + it, 80) }),
        )
        val crowded = MidiCoreRoleValidator.validate(
            context(CandidateRole.DRUMS, acceptedDependencies = crowdedDependencies),
            listOf(MidiCoreCandidateEvent.Note(0, 120, 36, 90)),
        )

        val advisory = assertAccepted(missingKick)
        assertTrue(advisory.findings.any { it.code == MidiCoreRoleFindingCode.KICK_BASS_INTENT_MISSING })
        assertRejected(crowded, MidiCoreRoleFindingCode.ENSEMBLE_ONSET_DENSITY_EXCEEDED)
    }

    @Test
    fun `duplicate and density violations reject while deliberate silence is valid`() {
        val base = context(CandidateRole.CHORDS)
        val duplicate = MidiCoreRoleValidator.validate(
            base,
            listOf(
                MidiCoreCandidateEvent.Note(0, 480, 60, 80),
                MidiCoreCandidateEvent.Note(0, 480, 60, 80),
            ),
        )
        val dense = MidiCoreRoleValidator.validate(
            base,
            (0 until 9).map { index ->
                MidiCoreCandidateEvent.Note(index * 120L, index * 120L + 120, listOf(60, 64, 67)[index % 3], 80)
            },
        )
        val deliberateSilence = MidiCoreRoleValidator.validate(
            context(CandidateRole.CHORDS, density = 0.0),
            emptyList(),
        )

        assertRejected(duplicate, MidiCoreRoleFindingCode.DUPLICATE_NOTE)
        assertRejected(dense, MidiCoreRoleFindingCode.DENSITY_EXCEEDED)
        assertAccepted(deliberateSilence)
    }

    @Test
    fun `drums require percussion channel and curated GM starter pitches`() {
        val invalidPitch = MidiCoreRoleValidator.validate(
            context(CandidateRole.DRUMS),
            listOf(MidiCoreCandidateEvent.Note(0, 120, 40, 80)),
        )
        val invalidChannel = MidiCoreRoleValidator.validate(
            context(CandidateRole.DRUMS),
            MidiCoreRoleCandidate(
                CandidateRole.DRUMS,
                "verse-1",
                0,
                listOf(MidiCoreCandidateEvent.Note(0, 120, 36, 80)),
            ),
        )

        assertRejected(invalidPitch, MidiCoreRoleFindingCode.INVALID_DRUM_PITCH)
        assertRejected(invalidChannel, MidiCoreRoleFindingCode.WRONG_CHANNEL)
    }

    @Test
    fun `validation evidence is deterministic regardless of event order`() {
        val generationContext = context(CandidateRole.DRUMS)
        val ordered = listOf(
            MidiCoreCandidateEvent.Note(0, 120, 36, 80),
            MidiCoreCandidateEvent.Note(0, 120, 38, 90),
            MidiCoreCandidateEvent.Note(480, 600, 42, 76),
        )

        val first = assertAccepted(MidiCoreRoleValidator.validate(generationContext, ordered))
        val second = assertAccepted(MidiCoreRoleValidator.validate(generationContext, ordered.reversed()))

        assertEquals(first, second)
        assertEquals(generationContext.contextSha256, first.contextSha256)
        assertTrue(first.candidateSha256.matches(Regex("[0-9a-f]{64}")))
    }

    private fun assertAccepted(result: MidiCoreRoleValidationResult): MidiCoreRoleValidationReport {
        assertTrue(result is MidiCoreRoleValidationResult.Accepted, "Expected accepted result, got ${result.report.findings}")
        return result.report
    }

    private fun assertRejected(
        result: MidiCoreRoleValidationResult,
        expectedCode: MidiCoreRoleFindingCode,
    ): MidiCoreRoleValidationReport {
        assertTrue(result is MidiCoreRoleValidationResult.Rejected, "Expected rejected result, got ${result.report.findings}")
        assertTrue(result.report.blockers.any { it.code == expectedCode }, "Missing $expectedCode in ${result.report.findings}")
        assertFalse(result.report.passed)
        return result.report
    }

    private fun context(
        role: CandidateRole,
        chordSymbol: String = "C",
        protectedMelodyNotes: List<MidiCoreProtectedMelodyNote> = emptyList(),
        density: Double = 0.5,
        acceptedDependencies: List<MidiCoreAcceptedDependencyContext> = emptyList(),
    ): MidiCoreGenerationContext {
        val patternId = when (role) {
            CandidateRole.CHORDS -> MidiCoreChordRhythmPatternId.SUSTAINED.id
            CandidateRole.BASS -> MidiCoreBassPatternId.SUSTAINED_ROOT.id
            CandidateRole.DRUMS -> MidiCoreDrumGroovePatternId.DUSTY_STRAIGHT.id
        }
        val profileId = when (role) {
            CandidateRole.CHORDS -> "chords.sustained"
            CandidateRole.BASS -> "bass.sustained-sub-like"
            CandidateRole.DRUMS -> "drums.dusty"
        }
        return MidiCoreGenerationContext.forOccurrence(
            authority = MidiCoreAuthoritySnapshot.from(project(chordSymbol)),
            role = role,
            occurrenceId = "verse-1",
            performanceProfile = MidiCorePerformanceProfileCatalog.requireForRole(role, profileId),
            patternId = patternId,
            generator = MidiCoreGeneratorInput("test-generator", "test-v1", patternId, 11),
            protectedMelodyNotes = protectedMelodyNotes,
            acceptedDependencies = acceptedDependencies,
            sectionPolicy = MidiCoreSectionPolicy(density = density),
        )
    }

    private fun dependency(role: CandidateRole, notes: List<MidiCoreGenerationNote>): MidiCoreAcceptedDependencyContext =
        MidiCoreAcceptedDependencyContext(
            MidiCoreAcceptedDependency(role, "verse-1", "${role.name.lowercase()}-accepted", "d".repeat(64)),
            notes,
        )

    private fun protectedNote(pitch: Int, anchor: Boolean): MidiCoreProtectedMelodyNote = MidiCoreProtectedMelodyNote(
        id = "pmn-" + (if (anchor) "a" else "b").repeat(64),
        startTick = 0,
        endTick = 480,
        pitch = pitch,
        velocity = 90,
        anchor = anchor,
    )

    private fun project(chordSymbol: String): MidiCoreProject = MidiCoreProject(
        id = ProjectId("role-validation-project"),
        metadata = ProjectMetadata("Role validation", "2026-08-27T00:00:00Z"),
        sourceMidi = SourceMidiRecord(
            originalFilename = "source.mid",
            sha256 = "a".repeat(64),
            format = 1,
            ppq = 480,
            original = ProjectArtifact(ProjectRelativePath("source/original.mid"), "a".repeat(64)),
            importReport = ProjectArtifact(ProjectRelativePath("reports/import.json"), "b".repeat(64)),
            trackSummaries = listOf(MidiTrackSummary(0, "Melody", emptyList())),
            sourceEndTick = 1_920,
        ),
        selectedMelody = SelectedMelodyTrack(0, 0, "c".repeat(64)),
        authority = ProjectAuthority(
            key = ProjectKey(ProjectKeySpelling.C, ProjectScaleMode.MAJOR),
            tempo = ProjectTempo(500_000),
            meter = ProjectMeter(4, 2),
            sectionDefinitions = listOf(ProjectSectionDefinition("verse", "Verse")),
            occurrences = listOf(ProjectSectionOccurrence("verse-1", "verse", "Verse", 0, 1_920)),
            chordEvents = listOf(AuthoritativeChordEvent("verse-chord", "verse-1", chordSymbol, 0, 1_920)),
        ),
    )
}
