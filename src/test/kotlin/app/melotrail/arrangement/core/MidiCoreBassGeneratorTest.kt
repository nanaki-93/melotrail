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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class MidiCoreBassGeneratorTest {
    @Test
    fun `generates every curated bass pattern with both MIDI performance profiles`() {
        val results = MidiCoreBassPatternId.entries.flatMap { pattern ->
            listOf("bass.sustained-sub-like", "bass.muted-plucked").map { profile ->
                MidiCoreBassGenerator.generate(context(pattern.id, profile))
            }
        }

        assertTrue(results.all(MidiCoreBassGenerationResult::accepted), results.flatMap { it.validation.report.findings }.toString())
        assertTrue(results.all { it.candidate.channel == MidiCoreBassGenerator.MIDI_CHANNEL })
        assertTrue(results.all { result ->
            result.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>().all { it.pitch in 28..55 }
        })
        assertTrue(results.all { result ->
            result.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>().all { note ->
                note.startTick >= 0 && note.endTick <= 1_920 && note.endTick > note.startTick
            }
        })
    }

    @Test
    fun `matches the semantic golden sequence for every curated bass pattern`() {
        val generated = MidiCoreBassPatternId.entries.associate { pattern ->
            pattern.id to MidiCoreBassGenerator.generate(context(pattern.id, "bass.muted-plucked"))
        }
        val golden = goldenPatterns()

        assertTrue(generated.values.all(MidiCoreBassGenerationResult::accepted))
        generated.forEach { (pattern, result) ->
            assertEquals(golden.getValue(pattern), result.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>().map(::goldenNote))
        }
    }

    @Test
    fun `honors slash bass and exact sub-bar harmony windows`() {
        val result = MidiCoreBassGenerator.generate(
            context(
                patternId = MidiCoreBassPatternId.ROOT_FIFTH.id,
                profileId = "bass.muted-plucked",
                project = project(
                    chordEvents = listOf(
                        AuthoritativeChordEvent("first", "verse-1", "G/B", 0, 960),
                        AuthoritativeChordEvent("second", "verse-1", "Cmaj7", 960, 1_920),
                        AuthoritativeChordEvent("chorus", "chorus-1", "F", 1_920, 3_840),
                    ),
                ),
            ),
        )
        val notes = result.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>()

        assertTrue(result.accepted, "Expected valid slash-bass candidate, got ${result.validation.report.findings}")
        assertEquals(11, notes.first().pitch % 12)
        assertTrue(notes.all { note ->
            val window = result.context.chordWindows.single { it.startTick < note.endTick && note.startTick < it.endTick }
            window.chord.containsPitchClass(note.pitch)
        })
        assertTrue(notes.filter { it.startTick < 960 }.all { it.endTick <= 960 })
        assertTrue(notes.filter { it.startTick >= 960 }.all { it.endTick <= 1_920 })
    }

    @Test
    fun `profile note lengths distinguish sustained sub-like and muted plucked output`() {
        val sustained = MidiCoreBassGenerator.generate(
            context(MidiCoreBassPatternId.SUSTAINED_ROOT.id, "bass.sustained-sub-like"),
        )
        val muted = MidiCoreBassGenerator.generate(
            context(MidiCoreBassPatternId.SUSTAINED_ROOT.id, "bass.muted-plucked"),
        )
        val sustainedNote = sustained.candidate.events.single() as MidiCoreCandidateEvent.Note
        val mutedNote = muted.candidate.events.single() as MidiCoreCandidateEvent.Note

        assertEquals(1_920L, sustainedNote.endTick)
        assertEquals(1_440L, mutedNote.endTick)
        assertTrue(sustained.accepted)
        assertTrue(muted.accepted)
    }

    @Test
    fun `walking and diatonic approach patterns remain legal and voice lead across phrase boundaries`() {
        val walking = MidiCoreBassGenerator.generate(
            context(
                patternId = MidiCoreBassPatternId.WALK_TO_NEXT_ROOT.id,
                project = twoOccurrenceProject(),
            ),
        )
        val approach = MidiCoreBassGenerator.generate(
            context(
                patternId = MidiCoreBassPatternId.DIATONIC_APPROACH.id,
                project = twoOccurrenceProject(),
            ),
        )
        val walkingNotes = walking.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>()
        val approachNotes = approach.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>()

        assertTrue(walking.accepted, "Walking candidate findings: ${walking.validation.report.findings}")
        assertTrue(approach.accepted, "Approach candidate findings: ${approach.validation.report.findings}")
        assertTrue((walkingNotes + approachNotes).zipWithNext().all { (left, right) -> abs(right.pitch - left.pitch) <= 12 })
        assertEquals(4, walkingNotes.size)
        assertEquals(4, approachNotes.size)
        assertEquals(4, approachNotes[1].pitch % 12, "approach=${approachNotes.map { it.pitch }}")
    }

    @Test
    fun `accepted chord rhythm and melody activity affect bass attacks without changing authority`() {
        val rhythmicDependency = MidiCoreAcceptedDependencyContext(
            MidiCoreAcceptedDependency(CandidateRole.CHORDS, "verse-1", "chords-accepted", "d".repeat(64)),
            listOf(
                MidiCoreGenerationNote(0, 240, 60, 80),
                MidiCoreGenerationNote(720, 960, 64, 80),
                MidiCoreGenerationNote(1_200, 1_440, 67, 80),
                MidiCoreGenerationNote(1_680, 1_920, 60, 80),
            ),
        )
        val sparse = MidiCoreBassGenerator.generate(
            context(MidiCoreBassPatternId.ROOT_FIFTH.id, acceptedDependencies = listOf(rhythmicDependency)),
        )
        val dense = MidiCoreBassGenerator.generate(
            context(
                MidiCoreBassPatternId.ROOT_FIFTH.id,
                acceptedDependencies = listOf(rhythmicDependency),
                protectedMelodyNotes = (0 until 8).map { index ->
                    protectedNote(start = index * 240L, pitch = 72 + index % 3, anchor = false, suffix = index)
                },
            ),
        )

        assertTrue(sparse.accepted, "Rhythmic candidate findings: ${sparse.validation.report.findings}")
        assertTrue(dense.accepted, "Dense-melody candidate findings: ${dense.validation.report.findings}")
        assertEquals(listOf(0L, 720L, 1_200L, 1_680L), starts(sparse))
        assertTrue(dense.candidate.events.size < sparse.candidate.events.size)
        assertEquals(sparse.context.authorityHash, dense.context.authorityHash)
    }

    @Test
    fun `protected melody anchors and accepted low-end chord notes are avoided when alternatives exist`() {
        val result = MidiCoreBassGenerator.generate(
            context(
                patternId = MidiCoreBassPatternId.ROOT_FIFTH.id,
                profileId = "bass.sustained-sub-like",
                protectedMelodyNotes = listOf(protectedNote(0, 36, true, 0)),
                acceptedDependencies = listOf(
                    MidiCoreAcceptedDependencyContext(
                        MidiCoreAcceptedDependency(CandidateRole.CHORDS, "verse-1", "chords-accepted", "e".repeat(64)),
                        listOf(MidiCoreGenerationNote(0, 1_920, 43, 80)),
                    ),
                ),
            ),
        )
        val notes = result.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>()

        assertTrue(result.accepted, "Expected space-aware bass candidate, got ${result.validation.report.findings}")
        assertTrue(notes.none { it.pitch == 36 })
        assertTrue(notes.none { it.pitch == 43 })
    }

    @Test
    fun `seeded alternatives are distinct repeatable and scoped to the bass channel`() {
        val generationContext = context(MidiCoreBassPatternId.ROOT_FIFTH.id)
        val alternatives = MidiCoreBassGenerator.generateAlternatives(generationContext, count = 5)
        val repeated = MidiCoreBassGenerator.generateAlternatives(generationContext, count = 5)

        assertEquals(5, alternatives.size)
        assertTrue(alternatives.all(MidiCoreBassGenerationResult::accepted))
        assertTrue(alternatives.all { it.candidate.channel == MidiCoreBassGenerator.MIDI_CHANNEL })
        assertEquals(alternatives, repeated)
        assertEquals(
            listOf(
                MidiCoreBassPatternId.ROOT_FIFTH,
                MidiCoreBassPatternId.OCTAVE,
                MidiCoreBassPatternId.WALK_TO_NEXT_ROOT,
                MidiCoreBassPatternId.DIATONIC_APPROACH,
                MidiCoreBassPatternId.SUSTAINED_ROOT,
            ).map(MidiCoreBassPatternId::id),
            alternatives.map { it.context.patternId },
        )
        assertTrue(alternatives.map { it.candidate.events }.distinct().size >= 3)
        assertTrue(alternatives.map { it.context.seed }.distinct().size == 5)
    }

    @Test
    fun `returns a scoped rejection for off-grid authority instead of silently moving a bass attack`() {
        val result = MidiCoreBassGenerator.generate(
            context(
                patternId = MidiCoreBassPatternId.SUSTAINED_ROOT.id,
                project = project(
                    chordEvents = listOf(
                        AuthoritativeChordEvent("first", "verse-1", "C", 0, 1),
                        AuthoritativeChordEvent("second", "verse-1", "F", 1, 1_920),
                        AuthoritativeChordEvent("chorus", "chorus-1", "F", 1_920, 3_840),
                    ),
                ),
            ),
        )

        assertFalse(result.accepted)
        assertTrue(result.validation is MidiCoreRoleValidationResult.Rejected)
        assertTrue(result.validation.report.blockers.any { it.code == MidiCoreRoleFindingCode.UNREPRESENTABLE_TICK })
        assertEquals(result.context.contextSha256, result.validation.report.contextSha256)
    }

    private fun starts(result: MidiCoreBassGenerationResult): List<Long> = result.candidate.events
        .filterIsInstance<MidiCoreCandidateEvent.Note>()
        .map(MidiCoreCandidateEvent.Note::startTick)

    private fun goldenPatterns(): Map<String, List<String>> = Json.parseToJsonElement(
        requireNotNull(javaClass.getResource("/fixtures/midi-core/bass-golden.json")) { "Missing Bass golden fixture" }.readText(),
    ).jsonObject.getValue("patterns").jsonObject.mapValues { (_, values) ->
        values.jsonArray.map { it.jsonPrimitive.content }
    }

    private fun goldenNote(note: MidiCoreCandidateEvent.Note): String =
        "${note.startTick}|${note.endTick}|${note.pitch}|${note.velocity}"

    private fun context(
        patternId: String,
        profileId: String = "bass.muted-plucked",
        seed: Long = 17,
        density: Double = 1.0,
        project: MidiCoreProject = project(),
        protectedMelodyNotes: List<MidiCoreProtectedMelodyNote> = emptyList(),
        acceptedDependencies: List<MidiCoreAcceptedDependencyContext> = emptyList(),
    ): MidiCoreGenerationContext = MidiCoreGenerationContext.forOccurrence(
        authority = MidiCoreAuthoritySnapshot.from(project),
        role = CandidateRole.BASS,
        occurrenceId = "verse-1",
        performanceProfile = MidiCorePerformanceProfileCatalog.requireForRole(CandidateRole.BASS, profileId),
        patternId = patternId,
        generator = MidiCoreGeneratorInput("test-generator", "test-v1", patternId, seed),
        protectedMelodyNotes = protectedMelodyNotes,
        acceptedDependencies = acceptedDependencies,
        sectionPolicy = MidiCoreSectionPolicy(density = density),
    )

    private fun protectedNote(start: Long, pitch: Int, anchor: Boolean, suffix: Int): MidiCoreProtectedMelodyNote = MidiCoreProtectedMelodyNote(
        id = "pmn-${suffix.toString(16).padStart(2, '0')}${"a".repeat(62)}",
        startTick = start,
        endTick = start + 120,
        pitch = pitch,
        velocity = 90,
        anchor = anchor,
    )

    private fun project(
        chordEvents: List<AuthoritativeChordEvent> = listOf(
            AuthoritativeChordEvent("verse-chord", "verse-1", "C", 0, 1_920),
            AuthoritativeChordEvent("chorus-chord", "chorus-1", "F", 1_920, 3_840),
        ),
    ): MidiCoreProject = MidiCoreProject(
        id = ProjectId("bass-generator-project"),
        metadata = ProjectMetadata("Bass generator", "2026-08-27T00:00:00Z"),
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
            sectionDefinitions = listOf(
                ProjectSectionDefinition("verse", "Verse"),
                ProjectSectionDefinition("chorus", "Chorus"),
            ),
            occurrences = listOf(
                ProjectSectionOccurrence("verse-1", "verse", "Verse", 0, 1_920),
                ProjectSectionOccurrence("chorus-1", "chorus", "Chorus", 1_920, 3_840),
            ),
            chordEvents = chordEvents,
        ),
    )

    private fun twoOccurrenceProject(): MidiCoreProject = project(
        chordEvents = listOf(
            AuthoritativeChordEvent("verse-first", "verse-1", "C", 0, 960),
            AuthoritativeChordEvent("verse-second", "verse-1", "F", 960, 1_920),
            AuthoritativeChordEvent("chorus-chord", "chorus-1", "F", 1_920, 3_840),
        ),
    )
}
