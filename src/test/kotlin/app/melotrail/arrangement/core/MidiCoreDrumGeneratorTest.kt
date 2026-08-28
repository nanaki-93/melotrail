package app.melotrail.arrangement.core

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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class MidiCoreDrumGeneratorTest {
    @Test
    fun `generates every complete groove with both MIDI drum profiles`() {
        val results = MidiCoreDrumGroovePatternId.entries.flatMap { pattern ->
            listOf("drums.dusty", "drums.lifted").map { profile ->
                MidiCoreDrumGenerator.generate(context(pattern.id, profileId = profile))
            }
        }

        assertTrue(results.all(MidiCoreDrumGenerationResult::accepted), results.flatMap { it.validation.report.findings }.toString())
        assertTrue(results.all { it.candidate.channel == MidiCoreDrumGenerator.MIDI_CHANNEL })
        assertTrue(results.all { result ->
            result.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>().all { note ->
                note.pitch in setOf(36, 38, 42, 46) && note.startTick >= 0 && note.endTick <= 1_920 && note.endTick > note.startTick
            }
        })
        assertTrue(results.all { it.candidate.events.size == MidiCorePatternCatalog.drumGrooves.single { pattern -> pattern.id == it.context.patternId }.steps.size })
    }

    @Test
    fun `drum catalog exposes every supported groove and fill exactly once`() {
        assertEquals(
            (MidiCoreDrumGroovePatternId.entries.map(MidiCoreDrumGroovePatternId::id) +
                MidiCoreDrumFillPatternId.entries.map(MidiCoreDrumFillPatternId::id)).toSet(),
            MidiCorePatternCatalog.allowedPatternIds(CandidateRole.DRUMS).toSet(),
        )
    }

    @Test
    fun `matches complete groove golden sequences without arbitrary hit deletion`() {
        val generated = MidiCoreDrumGroovePatternId.entries.associate { pattern ->
            pattern.id to MidiCoreDrumGenerator.generate(context(pattern.id)).candidate.events
                .filterIsInstance<MidiCoreCandidateEvent.Note>()
                .map(::semantic)
        }

        generated.forEach { (pattern, actual) -> assertEquals(goldenGrooves().getValue(pattern), actual) }
    }

    @Test
    fun `matches phrase fill golden sequences at the occurrence boundary`() {
        val generated = MidiCoreDrumFillPatternId.entries.associate { fill ->
            fill.id to MidiCoreDrumGenerator.generate(
                context(
                    MidiCoreDrumGroovePatternId.DUSTY_STRAIGHT.id,
                    fillPatternId = fill.id,
                ),
            ).candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>().map(::semantic)
        }

        generated.forEach { (fill, actual) -> assertEquals(goldenFills().getValue(fill), actual) }
    }

    @Test
    fun `density selects a whole compatible groove variant rather than deleting authored steps`() {
        val result = MidiCoreDrumGenerator.generate(
            context(
                MidiCoreDrumGroovePatternId.LIFT_BUILD.id,
                density = 0.5,
                project = project(1_920),
            ),
        )
        val notes = result.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>()
        val selectedStarts = notes.map { it.startTick to it.pitch }
        val dustyStarts = MidiCoreDrumGenerator.generate(
            context(MidiCoreDrumGroovePatternId.DUSTY_STRAIGHT.id, project = project(1_920)),
        ).candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>().map { it.startTick to it.pitch }

        assertTrue(result.accepted, "Density-selected groove findings: ${result.validation.report.findings}")
        assertEquals(dustyStarts, selectedStarts)
        assertNotEquals(
            MidiCoreDrumPatternCatalogSize.LIFT_BUILD_STEPS,
            notes.size,
            "The dense authored Lift build must not be partially decimated",
        )
    }

    @Test
    fun `phrase fills stay in the final bar and never cross the occurrence boundary`() {
        val withFill = MidiCoreDrumGenerator.generate(
            context(
                MidiCoreDrumGroovePatternId.DUSTY_STRAIGHT.id,
                project = project(3_840),
                fillPatternId = MidiCoreDrumFillPatternId.DUSTY_SNARE_ROLL.id,
            ),
        )
        val withoutFill = MidiCoreDrumGenerator.generate(
            context(MidiCoreDrumGroovePatternId.DUSTY_STRAIGHT.id, project = project(3_840)),
        )
        val notes = withFill.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>()

        assertTrue(withFill.accepted, "Fill candidate findings: ${withFill.validation.report.findings}")
        assertTrue(withoutFill.accepted)
        assertEquals(withoutFill.candidate.events.size + 3, withFill.candidate.events.size)
        assertTrue(notes.filter { it.startTick >= 3_360 }.map { it.startTick }.containsAll(listOf(3_480L, 3_600L, 3_720L)))
        assertTrue(notes.none { it.startTick < 1_920 && it.startTick >= 1_800 })
        assertTrue(notes.all { it.startTick in 0 until 3_840 && it.endTick <= 3_840 })
    }

    @Test
    fun `accepted bass attacks can add deterministic offbeat kick intent`() {
        val bassDependency = MidiCoreAcceptedDependencyContext(
            MidiCoreAcceptedDependency(CandidateRole.BASS, "verse-1", "bass-accepted", "d".repeat(64)),
            listOf(
                MidiCoreGenerationNote(720, 840, 36, 80),
                MidiCoreGenerationNote(1_200, 1_320, 36, 80),
                MidiCoreGenerationNote(0, 480, 36, 80),
            ),
        )
        val result = MidiCoreDrumGenerator.generate(
            context(
                MidiCoreDrumGroovePatternId.DUSTY_STRAIGHT.id,
                acceptedDependencies = listOf(bassDependency),
            ),
        )
        val kickStarts = result.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>()
            .filter { it.pitch == 36 }
            .map { it.startTick }

        assertTrue(result.accepted, "Bass-aware candidate findings: ${result.validation.report.findings}")
        assertEquals(listOf(0L, 720L, 960L, 1_200L), kickStarts)
        assertEquals(result, MidiCoreDrumGenerator.generate(
            context(
                MidiCoreDrumGroovePatternId.DUSTY_STRAIGHT.id,
                acceptedDependencies = listOf(bassDependency),
            ),
        ))
    }

    @Test
    fun `bass-aware kicks are restrained per bar and never crowd an explicit final-bar fill`() {
        val bassDependency = MidiCoreAcceptedDependencyContext(
            MidiCoreAcceptedDependency(CandidateRole.BASS, "verse-1", "bass-accepted", "f".repeat(64)),
            listOf(240L, 720L, 1_200L, 1_680L, 2_160L, 2_640L, 3_120L, 3_600L)
                .map { start -> MidiCoreGenerationNote(start, start + 120, 36, 80) },
        )
        val withFill = MidiCoreDrumGenerator.generate(
            context(
                MidiCoreDrumGroovePatternId.DUSTY_STRAIGHT.id,
                project = project(3_840),
                fillPatternId = MidiCoreDrumFillPatternId.DUSTY_SNARE_ROLL.id,
                acceptedDependencies = listOf(bassDependency),
            ),
        )
        val plain = MidiCoreDrumGenerator.generate(
            context(MidiCoreDrumGroovePatternId.DUSTY_STRAIGHT.id, project = project(3_840)),
        )
        val addedKicks = withFill.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>()
            .filter { it.pitch == 36 && it.startTick !in plain.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>().filter { note -> note.pitch == 36 }.map { it.startTick }.toSet() }

        assertTrue(withFill.accepted, withFill.validation.report.findings.toString())
        assertEquals(listOf(720L, 1_200L), addedKicks.map { it.startTick })
        assertTrue(addedKicks.groupBy { it.startTick / 1_920 }.values.all { it.size <= 2 })
        assertTrue(addedKicks.none { it.startTick >= 1_920 })
    }

    @Test
    fun `intro context leaves accepted bass offbeats to the authored sparse groove`() {
        val bassDependency = MidiCoreAcceptedDependencyContext(
            MidiCoreAcceptedDependency(CandidateRole.BASS, "verse-1", "bass-accepted", "1".repeat(64)),
            listOf(MidiCoreGenerationNote(720, 840, 36, 80), MidiCoreGenerationNote(1_200, 1_320, 36, 80)),
        )
        val intro = MidiCoreDrumGenerator.generate(
            context(
                MidiCoreDrumGroovePatternId.DUSTY_STRAIGHT.id,
                purpose = MidiCoreSectionPurpose.INTRO,
                energy = 0.2,
                acceptedDependencies = listOf(bassDependency),
            ),
        )
        val authored = MidiCoreDrumGenerator.generate(
            context(MidiCoreDrumGroovePatternId.DUSTY_STRAIGHT.id, purpose = MidiCoreSectionPurpose.INTRO, energy = 0.2),
        )

        assertEquals(authored.candidate.events, intro.candidate.events)
    }

    @Test
    fun `direct fill selection uses a complete section-aware companion groove`() {
        val low = MidiCoreDrumGenerator.generate(
            context(
                MidiCoreDrumFillPatternId.SOFT_TWO_STROKE.id,
                energy = 0.2,
                purpose = MidiCoreSectionPurpose.INTRO,
            ),
        )
        val high = MidiCoreDrumGenerator.generate(
            context(
                MidiCoreDrumFillPatternId.SOFT_TWO_STROKE.id,
                energy = 0.9,
                purpose = MidiCoreSectionPurpose.CHORUS,
            ),
        )

        assertTrue(low.accepted && high.accepted)
        assertTrue(high.candidate.events.size > low.candidate.events.size)
        assertTrue(low.candidate.events.all { it is MidiCoreCandidateEvent.Note && it.pitch in setOf(36, 38, 42, 46) })
        assertTrue(high.candidate.events.all { it is MidiCoreCandidateEvent.Note && it.pitch in setOf(36, 38, 42, 46) })
    }

    @Test
    fun `GM pitches channel energy purpose and profile velocities remain deterministic`() {
        val low = MidiCoreDrumGenerator.generate(context(MidiCoreDrumGroovePatternId.DUSTY_STRAIGHT.id, energy = 0.0, purpose = MidiCoreSectionPurpose.INTRO))
        val high = MidiCoreDrumGenerator.generate(context(MidiCoreDrumGroovePatternId.DUSTY_STRAIGHT.id, energy = 1.0, purpose = MidiCoreSectionPurpose.CHORUS))
        val lifted = MidiCoreDrumGenerator.generate(context(MidiCoreDrumGroovePatternId.DUSTY_STRAIGHT.id, profileId = "drums.lifted"))
        val lowNotes = low.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>()
        val highNotes = high.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>()
        val liftedNotes = lifted.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>()

        assertTrue(low.accepted && high.accepted && lifted.accepted)
        assertTrue(lowNotes.all { it.pitch in setOf(36, 38, 42, 46) })
        assertTrue(lowNotes.all { it.velocity < highNotes[lowNotes.indexOf(it)].velocity })
        assertTrue(liftedNotes.zip(lowNotes).all { (liftedNote, dustyNote) -> liftedNote.velocity > dustyNote.velocity })
        assertTrue(highNotes.first().velocity > highNotes[1].velocity)
    }

    @Test
    fun `complete groove alternatives are scoped repeatable and distinct`() {
        val generationContext = context(MidiCoreDrumGroovePatternId.LAZY_SWING.id)
        val alternatives = MidiCoreDrumGenerator.generateAlternatives(generationContext, count = 4)
        val repeated = MidiCoreDrumGenerator.generateAlternatives(generationContext, count = 4)

        assertEquals(4, alternatives.size)
        assertEquals(alternatives, repeated)
        assertTrue(alternatives.all(MidiCoreDrumGenerationResult::accepted))
        assertTrue(alternatives.all { it.candidate.channel == MidiCoreDrumGenerator.MIDI_CHANNEL })
        assertEquals(
            listOf(
                MidiCoreDrumGroovePatternId.LAZY_SWING.id,
                MidiCoreDrumGroovePatternId.HALF_TIME_POCKET.id,
                MidiCoreDrumGroovePatternId.LIFT_BUILD.id,
                MidiCoreDrumGroovePatternId.DUSTY_STRAIGHT.id,
            ),
            alternatives.map { it.context.patternId },
        )
        assertTrue(alternatives.map { it.candidate.events }.distinct().size == 4)
        assertEquals(4, alternatives.map { it.context.seed }.distinct().size)
    }

    @Test
    fun `zero density is deliberate silence while offgrid context remains unmodified`() {
        val silent = MidiCoreDrumGenerator.generate(context(MidiCoreDrumGroovePatternId.DUSTY_STRAIGHT.id, density = 0.0))
        val offgridBass = MidiCoreAcceptedDependencyContext(
            MidiCoreAcceptedDependency(CandidateRole.BASS, "verse-1", "bass-offgrid", "e".repeat(64)),
            listOf(MidiCoreGenerationNote(241, 360, 36, 80)),
        )
        val result = MidiCoreDrumGenerator.generate(
            context(MidiCoreDrumGroovePatternId.DUSTY_STRAIGHT.id, acceptedDependencies = listOf(offgridBass)),
        )
        val kickStarts = result.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>()
            .filter { it.pitch == 36 }
            .map { it.startTick }

        assertTrue(silent.accepted)
        assertTrue(silent.candidate.events.isEmpty())
        assertTrue(result.accepted)
        assertTrue(241L !in kickStarts)
    }

    @Test
    fun `three development fixtures yield two complete deterministic drum alternatives`() {
        val expectedCandidateHashes = mapOf(
            "low-energy-intro" to listOf(
                "7e0b2769a766527110c98db792281a0edbc4473f0e16f03dd308f72e2a47b3d4",
                "d6098777a62f8066a5269fb6131d2ba7ab303f317c61f8940f08a1a55ed4cc22",
            ),
            "chorus-lift-with-bass" to listOf(
                "740770c87475d431a8aa69da8e195e91d19eb0436b72859086e38682c9b23c29",
                "669b3db4622ad126531b7294d8079d2ab627bb5f6ed5992336ee69405caab167",
            ),
            "bridge-half-time-transition" to listOf(
                "5d306020a745b043fb2b65dbe28c7d22cfbb7bfed5ca4055a255f0c33d1f9daf",
                "69e3e0681ce5599bfd12f6df765f5fb3b537ca101703881f8a17f8369c01ac1e",
            ),
        )
        developmentFixtures().forEach { fixture ->
            val alternatives = MidiCoreDrumGenerator.generateAlternatives(fixture.context, count = 2)

            assertEquals(2, alternatives.size, fixture.name)
            assertTrue(alternatives.all(MidiCoreDrumGenerationResult::accepted), "$fixture -> ${alternatives.map { it.validation.report.findings }}")
            assertEquals(2, alternatives.map { it.validation.report.candidateSha256 }.toSet().size, fixture.name)
            assertEquals(expectedCandidateHashes.getValue(fixture.name), alternatives.map { it.validation.report.candidateSha256 })
            alternatives.forEach { alternative ->
                val notes = alternative.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>()
                assertTrue(notes.isNotEmpty(), fixture.name)
                assertTrue(notes.all { it.pitch in setOf(36, 38, 42, 46) })
                assertTrue(notes.all { it.startTick >= alternative.context.occurrence.startTick && it.endTick <= alternative.context.occurrence.endTick })
                assertTrue(notes.groupBy { it.startTick to it.pitch }.all { (_, values) -> values.size == 1 })
            }
        }
    }

    private fun semantic(note: MidiCoreCandidateEvent.Note): String =
        "${note.startTick}|${note.endTick}|${note.pitch}|${note.velocity}"

    private fun goldenGrooves(): Map<String, List<String>> = Json.parseToJsonElement(
        requireNotNull(javaClass.getResource("/fixtures/midi-core/drums-golden.json")) { "Missing Drum golden fixture" }.readText(),
    ).jsonObject.getValue("grooves").jsonObject.mapValues { (_, values) ->
        values.jsonArray.map { it.jsonPrimitive.content }
    }

    private fun goldenFills(): Map<String, List<String>> = Json.parseToJsonElement(
        requireNotNull(javaClass.getResource("/fixtures/midi-core/drums-golden.json")) { "Missing Drum golden fixture" }.readText(),
    ).jsonObject.getValue("fills").jsonObject.mapValues { (_, values) ->
        values.jsonArray.map { it.jsonPrimitive.content }
    }

    private fun context(
        patternId: String,
        profileId: String = "drums.dusty",
        seed: Long = 17,
        density: Double = 1.0,
        energy: Double = 0.5,
        purpose: MidiCoreSectionPurpose = MidiCoreSectionPurpose.VERSE,
        project: MidiCoreProject = project(1_920),
        fillPatternId: String? = null,
        acceptedDependencies: List<MidiCoreAcceptedDependencyContext> = emptyList(),
    ): MidiCoreGenerationContext = MidiCoreGenerationContext.forOccurrence(
        authority = MidiCoreAuthoritySnapshot.from(project),
        role = CandidateRole.DRUMS,
        occurrenceId = "verse-1",
        performanceProfile = MidiCorePerformanceProfileCatalog.requireForRole(CandidateRole.DRUMS, profileId),
        patternId = patternId,
        generator = MidiCoreGeneratorInput("test-generator", "test-v1", patternId, seed),
        acceptedDependencies = acceptedDependencies,
        sectionPolicy = MidiCoreSectionPolicy(purpose, energy, density, fillPatternId),
    )

    private fun project(endTick: Long): MidiCoreProject = MidiCoreProject(
        id = ProjectId("drum-generator-project"),
        metadata = ProjectMetadata("Drum generator", "2026-08-27T00:00:00Z"),
        sourceMidi = SourceMidiRecord(
            originalFilename = "source.mid",
            sha256 = "a".repeat(64),
            format = 1,
            ppq = 480,
            original = ProjectArtifact(ProjectRelativePath("source/original.mid"), "a".repeat(64)),
            importReport = ProjectArtifact(ProjectRelativePath("reports/import.json"), "b".repeat(64)),
            trackSummaries = listOf(MidiTrackSummary(0, "Melody", emptyList())),
            sourceEndTick = endTick,
        ),
        selectedMelody = SelectedMelodyTrack(0, 0, "c".repeat(64)),
        authority = ProjectAuthority(
            key = ProjectKey(ProjectKeySpelling.C, ProjectScaleMode.MAJOR),
            tempo = ProjectTempo(500_000),
            meter = ProjectMeter(4, 2),
            sectionDefinitions = listOf(ProjectSectionDefinition("verse", "Verse")),
            occurrences = listOf(ProjectSectionOccurrence("verse-1", "verse", "Verse", 0, endTick)),
            chordEvents = listOf(AuthoritativeChordEvent("verse-chord", "verse-1", "C", 0, endTick)),
        ),
    )

    private object MidiCoreDrumPatternCatalogSize {
        const val LIFT_BUILD_STEPS = 20
    }

    private fun developmentFixtures(): List<DevelopmentFixture> = listOf(
        DevelopmentFixture(
            "low-energy-intro",
            context(MidiCoreDrumGroovePatternId.DUSTY_STRAIGHT.id, energy = 0.2, purpose = MidiCoreSectionPurpose.INTRO),
        ),
        DevelopmentFixture(
            "chorus-lift-with-bass",
            context(
                MidiCoreDrumGroovePatternId.LIFT_BUILD.id,
                energy = 0.9,
                purpose = MidiCoreSectionPurpose.CHORUS,
                project = project(3_840),
                fillPatternId = MidiCoreDrumFillPatternId.KICK_SNARE_TURNAROUND.id,
                acceptedDependencies = listOf(
                    MidiCoreAcceptedDependencyContext(
                        MidiCoreAcceptedDependency(CandidateRole.BASS, "verse-1", "bass-accepted", "2".repeat(64)),
                        listOf(MidiCoreGenerationNote(720, 840, 36, 80), MidiCoreGenerationNote(1_200, 1_320, 36, 80)),
                    ),
                ),
            ),
        ),
        DevelopmentFixture(
            "bridge-half-time-transition",
            context(
                MidiCoreDrumGroovePatternId.HALF_TIME_POCKET.id,
                energy = 0.45,
                purpose = MidiCoreSectionPurpose.BRIDGE,
                fillPatternId = MidiCoreDrumFillPatternId.BRIDGE_HALF_TIME_BREAK.id,
            ),
        ),
    )

    private data class DevelopmentFixture(val name: String, val context: MidiCoreGenerationContext)
}
