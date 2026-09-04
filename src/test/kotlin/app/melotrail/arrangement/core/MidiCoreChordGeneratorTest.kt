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

class MidiCoreChordGeneratorTest {
    @Test
    fun `generates every authoritative chord extension inside the bounded register`() {
        val result = MidiCoreChordGenerator.generate(
            context(chordSymbol = "Cmaj9", density = 1.0),
        )
        val notes = result.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>()

        assertTrue(result.accepted, "Expected a valid chord candidate, got ${result.validation.report.findings}")
        assertEquals(setOf(0, 2, 4, 7, 11), notes.map { it.pitch % 12 }.toSet())
        assertEquals(5, notes.size)
        assertTrue(notes.all { it.pitch in 48..84 && it.startTick == 0L && it.endTick == 1_920L })
    }

    @Test
    fun `expands complete curated chord rhythms without changing harmony`() {
        val patterns = listOf(
            MidiCoreChordRhythmPatternId.SUSTAINED,
            MidiCoreChordRhythmPatternId.LAID_BACK_QUARTERS,
            MidiCoreChordRhythmPatternId.LATE_ENTRY,
            MidiCoreChordRhythmPatternId.DUSTY_OFFBEATS,
            MidiCoreChordRhythmPatternId.BROKEN_SYNCOPATION,
            MidiCoreChordRhythmPatternId.BRIDGE_HALF_TIME,
        )
        val generated = patterns.associateWith { pattern ->
            MidiCoreChordGenerator.generate(
                context(patternId = pattern.id, profileId = "chords.pulsed", density = 1.0),
            )
        }

        assertTrue(generated.values.all { it.accepted }, generated.values.flatMap { it.validation.report.findings }.toString())
        assertEquals(listOf(0L), starts(generated.getValue(MidiCoreChordRhythmPatternId.SUSTAINED)))
        assertEquals(listOf(0L, 480L, 960L, 1_440L), starts(generated.getValue(MidiCoreChordRhythmPatternId.LAID_BACK_QUARTERS)))
        assertEquals(listOf(480L, 960L, 1_440L), starts(generated.getValue(MidiCoreChordRhythmPatternId.LATE_ENTRY)))
        assertEquals(listOf(240L, 720L, 1_200L, 1_680L), starts(generated.getValue(MidiCoreChordRhythmPatternId.DUSTY_OFFBEATS)))
        assertEquals(listOf(0L, 720L, 1_200L, 1_680L), starts(generated.getValue(MidiCoreChordRhythmPatternId.BROKEN_SYNCOPATION)))
        assertEquals(listOf(0L, 960L), starts(generated.getValue(MidiCoreChordRhythmPatternId.BRIDGE_HALF_TIME)))
        generated.values.forEach { result ->
            assertEquals(setOf(0, 4, 7), result.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>().map { it.pitch % 12 }.toSet())
        }
        val golden = goldenPatterns()
        generated.forEach { (pattern, result) ->
            assertEquals(golden.getValue(pattern.id), result.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>().map(::goldenNote))
        }
    }

    @Test
    fun `honors slash-bass inversion and carries nearby voice leading across sub-bar changes`() {
        val result = MidiCoreChordGenerator.generate(
            context(
                project = project(
                    chordEvents = listOf(
                        AuthoritativeChordEvent("first", "verse-1", "G/B", 0, 960),
                        AuthoritativeChordEvent("second", "verse-1", "Cmaj7", 960, 1_920),
                    ),
                ),
                density = 1.0,
            ),
        )
        val voicings = result.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>()
            .groupBy(MidiCoreCandidateEvent.Note::startTick)
            .toSortedMap()
            .values
            .map { notes -> notes.sortedBy(MidiCoreCandidateEvent.Note::pitch).map(MidiCoreCandidateEvent.Note::pitch) }

        assertTrue(result.accepted, "Expected a valid sub-bar candidate, got ${result.validation.report.findings}")
        assertEquals(11, voicings[0].first() % 12)
        assertEquals(setOf(2, 7, 11), voicings[0].map { it % 12 }.toSet())
        assertEquals(setOf(0, 4, 7, 11), voicings[1].map { it % 12 }.toSet(), "voicings=$voicings")
        assertTrue(voicings[0].zip(voicings[1]).all { (before, after) -> abs(after - before) <= 12 })
        assertTrue(voicings.flatten().all { it in 48..84 })
        assertTrue(result.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>().all { note ->
            if (note.startTick < 960) note.endTick <= 960 else note.endTick <= 1_920
        })
    }

    @Test
    fun `uses protected melody and accepted bass space when selecting a voicing`() {
        val result = MidiCoreChordGenerator.generate(
            context(
                protectedMelodyNotes = listOf(protectedNote(60, true)),
                acceptedDependencies = listOf(
                    MidiCoreAcceptedDependencyContext(
                        MidiCoreAcceptedDependency(CandidateRole.BASS, "verse-1", "bass-accepted", "d".repeat(64)),
                        listOf(MidiCoreGenerationNote(0, 1_920, 55, 80)),
                    ),
                ),
                density = 1.0,
            ),
        )
        val notes = result.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>()

        assertTrue(result.accepted, "Expected space-aware candidate, got ${result.validation.report.findings}")
        assertFalse(notes.any { it.pitch == 60 })
        assertTrue(notes.all { note -> abs(note.pitch - 55) > 5 })
    }

    @Test
    fun `uses a safe reduced voicing when every complete extension doubles protected melody anchors`() {
        val melodyAnchors = listOf(48, 60, 72, 84).mapIndexed { index, pitch ->
            MidiCoreProtectedMelodyNote(
                id = "pmn-" + index.toString(16).repeat(64),
                startTick = 0,
                endTick = 1_920,
                pitch = pitch,
                velocity = 90,
                anchor = true,
            )
        }
        val result = MidiCoreChordGenerator.generate(
            context(
                chordSymbol = "Cmaj9",
                profileId = "chords.pulsed",
                patternId = MidiCoreChordRhythmPatternId.BRIDGE_HALF_TIME.id,
                density = 0.5,
                protectedMelodyNotes = melodyAnchors,
            ),
        )
        val notes = result.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>()

        assertTrue(result.accepted, "Expected an anchor-safe partial voicing, got ${result.validation.report.findings}")
        assertTrue(notes.map { it.pitch % 12 }.toSet().let { it.size >= 2 && it.all { pitchClass -> pitchClass in setOf(2, 4, 7, 11) } })
        assertEquals(2, notes.map { it.startTick }.distinct().size)
        assertFalse(notes.any { it.pitch in melodyAnchors.map(MidiCoreProtectedMelodyNote::pitch) })
        assertFalse(result.validation.report.findings.any { it.code == MidiCoreRoleFindingCode.DENSITY_EXCEEDED })
    }

    @Test
    fun `retains the declared slash bass when reduced voicing is required`() {
        val melodyAnchors = listOf(48, 60, 72, 84).mapIndexed { index, pitch ->
            MidiCoreProtectedMelodyNote("pmn-" + index.toString(16).repeat(64), 0, 1_920, pitch, 90, anchor = true)
        }
        val result = MidiCoreChordGenerator.generate(
            context(
                chordSymbol = "C/E",
                profileId = "chords.pulsed",
                patternId = MidiCoreChordRhythmPatternId.BRIDGE_HALF_TIME.id,
                density = 0.5,
                protectedMelodyNotes = melodyAnchors,
            ),
        )

        assertTrue(result.accepted, "Expected a safe slash-bass guide, got ${result.validation.report.findings}")
        result.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>()
            .groupBy(MidiCoreCandidateEvent.Note::startTick)
            .values
            .forEach { voicing -> assertEquals(4, voicing.minOf(MidiCoreCandidateEvent.Note::pitch) % 12) }
    }

    @Test
    fun `seed and curated pattern identity produce repeatable distinct alternatives`() {
        val generationContext = context(density = 1.0)
        val alternatives = MidiCoreChordGenerator.generateAlternatives(generationContext, count = 2)
        val repeated = MidiCoreChordGenerator.generateAlternatives(generationContext, count = 2)

        assertEquals(2, alternatives.size)
        assertTrue(alternatives.all(MidiCoreChordGenerationResult::accepted))
        assertTrue(alternatives[0].candidate.events != alternatives[1].candidate.events)
        assertEquals(alternatives, repeated)
        assertEquals(
            listOf(MidiCoreChordRhythmPatternId.SUSTAINED.id, MidiCoreChordRhythmPatternId.LAID_BACK_QUARTERS.id),
            alternatives.map { it.context.patternId },
        )
        assertTrue(alternatives.map { it.context.seed }.distinct().size == 2)
    }

    @Test
    fun `returns a scoped rejection when authoritative boundaries are off the generation grid`() {
        val result = MidiCoreChordGenerator.generate(
            context(
                project = project(
                    chordEvents = listOf(
                        AuthoritativeChordEvent("first", "verse-1", "C", 0, 1),
                        AuthoritativeChordEvent("second", "verse-1", "F", 1, 1_920),
                    ),
                ),
                density = 1.0,
            ),
        )

        assertFalse(result.accepted)
        assertTrue(result.validation is MidiCoreRoleValidationResult.Rejected)
        assertTrue(result.validation.report.blockers.any { it.code == MidiCoreRoleFindingCode.UNREPRESENTABLE_TICK })
        assertEquals(result.context.contextSha256, result.validation.report.contextSha256)
    }

    private fun starts(result: MidiCoreChordGenerationResult): List<Long> = result.candidate.events
        .filterIsInstance<MidiCoreCandidateEvent.Note>()
        .map(MidiCoreCandidateEvent.Note::startTick)
        .distinct()

    private fun goldenPatterns(): Map<String, List<String>> = Json.parseToJsonElement(
        requireNotNull(javaClass.getResource("/fixtures/midi-core/chords-golden.json")) { "Missing Chords golden fixture" }.readText(),
    ).jsonObject.getValue("patterns").jsonObject.mapValues { (_, values) ->
        values.jsonArray.map { it.jsonPrimitive.content }
    }

    private fun goldenNote(note: MidiCoreCandidateEvent.Note): String =
        "${note.startTick}|${note.endTick}|${note.pitch}|${note.velocity}"

    private fun context(
        patternId: String = MidiCoreChordRhythmPatternId.SUSTAINED.id,
        profileId: String = "chords.sustained",
        seed: Long = 17,
        density: Double = 1.0,
        chordSymbol: String = "C",
        project: MidiCoreProject = project(chordSymbol = chordSymbol),
        protectedMelodyNotes: List<MidiCoreProtectedMelodyNote> = emptyList(),
        acceptedDependencies: List<MidiCoreAcceptedDependencyContext> = emptyList(),
    ): MidiCoreGenerationContext = MidiCoreGenerationContext.forOccurrence(
        authority = MidiCoreAuthoritySnapshot.from(project),
        role = CandidateRole.CHORDS,
        occurrenceId = "verse-1",
        performanceProfile = MidiCorePerformanceProfileCatalog.requireForRole(CandidateRole.CHORDS, profileId),
        patternId = patternId,
        generator = MidiCoreGeneratorInput("test-generator", "test-v1", patternId, seed),
        protectedMelodyNotes = protectedMelodyNotes,
        acceptedDependencies = acceptedDependencies,
        sectionPolicy = MidiCoreSectionPolicy(density = density),
    )

    private fun protectedNote(pitch: Int, anchor: Boolean): MidiCoreProtectedMelodyNote = MidiCoreProtectedMelodyNote(
        id = "pmn-" + (if (anchor) "a" else "b").repeat(64),
        startTick = 0,
        endTick = 1_920,
        pitch = pitch,
        velocity = 90,
        anchor = anchor,
    )

    private fun project(
        chordSymbol: String = "C",
        chordEvents: List<AuthoritativeChordEvent> = listOf(
            AuthoritativeChordEvent("verse-chord", "verse-1", chordSymbol, 0, 1_920),
        ),
    ): MidiCoreProject = MidiCoreProject(
        id = ProjectId("chord-generator-project"),
        metadata = ProjectMetadata("Chord generator", "2026-08-27T00:00:00Z"),
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
            chordEvents = chordEvents,
        ),
    )
}
