package app.melotrail.arrangement.core

import app.melotrail.midi.domain.MidiTrackSummary
import app.melotrail.music.core.ProjectKeySpelling
import app.melotrail.music.core.ProjectMeter
import app.melotrail.music.core.ProjectScaleMode
import app.melotrail.music.core.ProjectTempo
import app.melotrail.project.AuthoritativeChordEvent
import app.melotrail.project.CandidateRole
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
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/** Development-set regressions for deterministic Chords alternatives and their musical safety policies. */
class MidiCoreChordDevelopmentFixtureTest {
    @Test
    fun `three development fixtures yield two valid distinct chord alternatives with exact harmony`() {
        val expectedCandidateHashes = mapOf(
            "simple-diatonic-4-4" to listOf(
                "e2c507647338097ba284c01899deecec8980baef1a05bd5e286fd4ff8d880691",
                "d9357f07482573e9d131388c0812a0a578764d548bcb0b424558f2385a21f494",
            ),
            "pickup-and-sub-bar-changes" to listOf(
                "9f5bfa092d988655f379a9bff97b9089083e6ae19a7833a9b09b85e185896e60",
                "146025a93333be4d161da047b5d5295125d860f1d2fd642947ece3454837be3d",
            ),
            "chromatic-expressive-controller-source" to listOf(
                "311ac3d1c71794113bbbdaef43cfa33ca0b5b858657bdc2a6f8165b39a416025",
                "fb5d5b34f8ebb2bf2dc6fa447eebc5d2b47818ea359be2d1186f8f1b9da29284",
            ),
        )
        developmentFixtures().forEach { fixture ->
            val alternatives = MidiCoreChordGenerator.generateAlternatives(fixture.context, count = 2)

            assertEquals(2, alternatives.size, fixture.name)
            assertTrue(alternatives.all(MidiCoreChordGenerationResult::accepted), "$fixture -> ${alternatives.map { it.validation.report.findings }}")
            assertEquals(expectedCandidateHashes.getValue(fixture.name), alternatives.map { it.validation.report.candidateSha256 }, fixture.name)
            assertEquals(2, alternatives.map { it.validation.report.candidateSha256 }.toSet().size, fixture.name)
            assertEquals(2, alternatives.map { it.candidate.events }.toSet().size, fixture.name)
            alternatives.forEach { alternative ->
                val notes = alternative.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>()
                assertTrue(notes.isNotEmpty(), fixture.name)
                assertTrue(notes.all { note ->
                    fixture.context.chordWindows.any { window ->
                        note.startTick >= window.startTick && note.endTick <= window.endTick &&
                            window.chord.containsPitchClass(note.pitch)
                    }
                }, "$fixture -> $notes")
                assertTrue(notes.all { note ->
                    fixture.context.protectedMelodyNotes.none { melody ->
                        melody.anchor && melody.pitch == note.pitch && melody.overlaps(note.startTick, note.endTick)
                    }
                }, fixture.name)
            }
        }
    }

    @Test
    fun `chord rhythm catalog is complete unique and bar-bounded`() {
        val patterns = MidiCorePatternCatalog.chordRhythms

        assertEquals(MidiCoreChordRhythmPatternId.entries.toSet(), patterns.map(MidiCoreChordRhythmPattern::id).toSet())
        assertEquals(patterns.size, patterns.map { it.id.id }.toSet().size)
        assertTrue(patterns.all { pattern ->
            pattern.steps.isNotEmpty() && pattern.steps == pattern.steps.sortedBy(MidiCoreChordRhythmStep::sixteenth) &&
                pattern.steps.all { step -> step.sixteenth + step.durationSixteenths <= 16 }
        })
    }

    @Test
    fun `retains a common pitch and limits matched voice movement at an exact chord boundary`() {
        val result = MidiCoreChordGenerator.generate(
            context(
                name = "common-tone",
                chordEvents = listOf(
                    chord("c", "verse-1", "C", 0, 960),
                    chord("f", "verse-1", "F", 960, 1_920),
                ),
                patternId = MidiCoreChordRhythmPatternId.SUSTAINED.id,
            ),
        )
        val voicings = result.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>()
            .groupBy(MidiCoreCandidateEvent.Note::startTick)
            .toSortedMap()
            .values
            .map { notes -> notes.map(MidiCoreCandidateEvent.Note::pitch).sorted() }

        assertTrue(result.accepted, result.validation.report.findings.toString())
        assertEquals(2, voicings.size)
        assertTrue(voicings[0].intersect(voicings[1].toSet()).isNotEmpty(), "voicings=$voicings")
        assertTrue(voicings[0].zip(voicings[1]).all { (before, after) -> abs(after - before) <= 12 }, "voicings=$voicings")
    }

    @Test
    fun `does not publish a colliding fallback voicing when no complete chord voicing is melody safe`() {
        val anchors = (48..84).filter { Math.floorMod(it, 12) in setOf(0, 4, 7) }.mapIndexed { index, pitch ->
            MidiCoreProtectedMelodyNote(
                id = "pmn-${index.toString(16).padStart(64, '0')}",
                startTick = 0,
                endTick = 1_920,
                pitch = pitch,
                velocity = 90,
                anchor = true,
            )
        }
        val result = MidiCoreChordGenerator.generate(
            context(name = "no-safe-voicing", protectedMelodyNotes = anchors),
        )

        assertFalse(result.accepted)
        assertTrue(result.candidate.events.isEmpty())
        assertTrue(result.validation.report.blockers.any { it.code == MidiCoreRoleFindingCode.EMPTY_OUTPUT })
    }

    @Test
    fun `repeated harmony develops deterministically from the explicit section policy`() {
        val occurrences = listOf(
            ProjectSectionOccurrence("verse-1", "verse", "Verse", 0, 1_920),
            ProjectSectionOccurrence("verse-2", "verse", "Verse", 1_920, 3_840),
        )
        val chords = listOf(
            chord("first", "verse-1", "C", 0, 1_920),
            chord("repeat", "verse-2", "C", 1_920, 3_840),
        )
        val project = project("repeated-development", occurrences, chords)
        val authority = MidiCoreAuthoritySnapshot.from(project)
        val first = MidiCoreChordGenerator.generate(context(
            authority = authority,
            occurrenceId = "verse-1",
            name = "repeated-low",
            sectionPolicy = MidiCoreSectionPolicy(MidiCoreSectionPurpose.VERSE, energy = 0.2, density = 1.0),
        ))
        val repeated = MidiCoreChordGenerator.generate(context(
            authority = authority,
            occurrenceId = "verse-2",
            name = "repeated-lift",
            sectionPolicy = MidiCoreSectionPolicy(MidiCoreSectionPurpose.CHORUS, energy = 0.9, density = 1.0),
        ))

        assertTrue(first.accepted, first.validation.report.findings.toString())
        assertTrue(repeated.accepted, repeated.validation.report.findings.toString())
        assertTrue(first.candidate.events != repeated.candidate.events)
        assertTrue(first.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>().map(MidiCoreCandidateEvent.Note::pitch) !=
            repeated.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>().map(MidiCoreCandidateEvent.Note::pitch))
    }

    private fun developmentFixtures(): List<DevelopmentFixture> = listOf(
        DevelopmentFixture(
            "simple-diatonic-4-4",
            context(
                name = "diatonic",
                chordEvents = listOf(
                    chord("c", "verse-1", "C", 0, 960),
                    chord("f", "verse-1", "F", 960, 1_920),
                    chord("g", "verse-1", "G", 1_920, 2_880),
                    chord("return", "verse-1", "C", 2_880, 3_840),
                ),
                occurrenceEndTick = 3_840,
                patternId = MidiCoreChordRhythmPatternId.LAID_BACK_QUARTERS.id,
                sectionPolicy = MidiCoreSectionPolicy(MidiCoreSectionPurpose.VERSE, energy = 0.45, density = 1.0),
                protectedMelodyNotes = listOf(protectedNote(72, 0, 960), protectedNote(74, 960, 1_920)),
            ),
        ),
        DevelopmentFixture(
            "pickup-and-sub-bar-changes",
            context(
                name = "pickup",
                chordEvents = listOf(
                    chord("pickup-c", "verse-1", "C", 0, 480),
                    chord("pickup-g", "verse-1", "G/B", 480, 960),
                    chord("pickup-am", "verse-1", "Am", 960, 1_920),
                ),
                pickupTicks = 120,
                patternId = MidiCoreChordRhythmPatternId.DUSTY_OFFBEATS.id,
                sectionPolicy = MidiCoreSectionPolicy(MidiCoreSectionPurpose.PRE_CHORUS, energy = 0.55, density = 1.0),
                protectedMelodyNotes = listOf(protectedNote(67, 0, 120), protectedNote(72, 360, 480)),
            ),
        ),
        DevelopmentFixture(
            "chromatic-expressive-controller-source",
            context(
                name = "expressive-controller",
                chordEvents = listOf(
                    chord("db", "verse-1", "Db", 0, 960),
                    chord("e7", "verse-1", "E7", 960, 1_920),
                ),
                patternId = MidiCoreChordRhythmPatternId.LAID_BACK_QUARTERS.id,
                sectionPolicy = MidiCoreSectionPolicy(MidiCoreSectionPurpose.CHORUS, energy = 0.8, density = 1.0),
                protectedMelodyNotes = listOf(protectedNote(69, 0, 480), protectedNote(73, 960, 1_920)),
            ),
        ),
    )

    private fun context(
        name: String,
        chordEvents: List<AuthoritativeChordEvent> = listOf(chord("c", "verse-1", "C", 0, 1_920)),
        occurrenceEndTick: Long = 1_920,
        pickupTicks: Long = 0,
        patternId: String = MidiCoreChordRhythmPatternId.SUSTAINED.id,
        sectionPolicy: MidiCoreSectionPolicy = MidiCoreSectionPolicy(density = 1.0),
        protectedMelodyNotes: List<MidiCoreProtectedMelodyNote> = emptyList(),
    ): MidiCoreGenerationContext {
        val occurrences = listOf(ProjectSectionOccurrence("verse-1", "verse", "Verse", 0, occurrenceEndTick))
        return context(
            authority = MidiCoreAuthoritySnapshot.from(project(name, occurrences, chordEvents, pickupTicks)),
            occurrenceId = "verse-1",
            name = name,
            patternId = patternId,
            sectionPolicy = sectionPolicy,
            protectedMelodyNotes = protectedMelodyNotes,
        )
    }

    private fun context(
        authority: MidiCoreAuthoritySnapshot,
        occurrenceId: String,
        name: String,
        patternId: String = MidiCoreChordRhythmPatternId.SUSTAINED.id,
        sectionPolicy: MidiCoreSectionPolicy = MidiCoreSectionPolicy(density = 1.0),
        protectedMelodyNotes: List<MidiCoreProtectedMelodyNote> = emptyList(),
    ): MidiCoreGenerationContext = MidiCoreGenerationContext.forOccurrence(
        authority = authority,
        role = CandidateRole.CHORDS,
        occurrenceId = occurrenceId,
        performanceProfile = MidiCorePerformanceProfileCatalog.requireForRole(CandidateRole.CHORDS, "chords.pulsed"),
        patternId = patternId,
        generator = MidiCoreGeneratorInput("development-$name", "mc-041", patternId, seed = 17),
        protectedMelodyNotes = protectedMelodyNotes,
        sectionPolicy = sectionPolicy,
    )

    private fun protectedNote(pitch: Int, startTick: Long, endTick: Long) = MidiCoreProtectedMelodyNote(
        id = "pmn-${"${pitch.toString(16)}${startTick.toString(16)}".padStart(64, '0')}",
        startTick = startTick,
        endTick = endTick,
        pitch = pitch,
        velocity = 90,
        anchor = true,
    )

    private fun project(
        name: String,
        occurrences: List<ProjectSectionOccurrence>,
        chords: List<AuthoritativeChordEvent>,
        pickupTicks: Long = 0,
    ) = MidiCoreProject(
        id = ProjectId("chord-development-$name"),
        metadata = ProjectMetadata("Chord development $name", "2026-08-28T00:00:00Z"),
        sourceMidi = SourceMidiRecord(
            originalFilename = "$name.mid",
            sha256 = "a".repeat(64),
            format = 1,
            ppq = 480,
            original = ProjectArtifact(ProjectRelativePath("source/original.mid"), "a".repeat(64)),
            importReport = ProjectArtifact(ProjectRelativePath("reports/import.json"), "b".repeat(64)),
            trackSummaries = listOf(MidiTrackSummary(0, "Melody", emptyList())),
            sourceEndTick = occurrences.last().endTick,
        ),
        selectedMelody = SelectedMelodyTrack(0, 0, "c".repeat(64)),
        authority = ProjectAuthority(
            key = ProjectKey(ProjectKeySpelling.C, ProjectScaleMode.MAJOR),
            tempo = ProjectTempo(500_000),
            meter = ProjectMeter(4, 2),
            sectionDefinitions = listOf(ProjectSectionDefinition("verse", "Verse")),
            occurrences = occurrences,
            chordEvents = chords,
            pickupTicks = pickupTicks,
        ),
    )

    private fun chord(id: String, occurrenceId: String, symbol: String, startTick: Long, endTick: Long) =
        AuthoritativeChordEvent(id, occurrenceId, symbol, startTick, endTick)

    private data class DevelopmentFixture(val name: String, val context: MidiCoreGenerationContext)
}
