package app.melotrail.arrangement.core

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
    fun `confirmed shared groove drives bounded bass kick support on the three four grid`() {
        val bassDependency = MidiCoreAcceptedDependencyContext(
            MidiCoreAcceptedDependency(CandidateRole.BASS, "verse-1", "bass-accepted", "d".repeat(64)),
            listOf(240L, 720L, 1_200L).map { start -> MidiCoreGenerationNote(start, start + 120, 36, 80) },
        )
        val drivingProject = project(
            endTick = 1_440,
            meter = ProjectMeter(3, 2),
            arrangementPlan = planFor(MidiCoreSharedGrooveIntent(MidiCoreGrooveFeel.STRAIGHT, MidiCoreGrooveSubdivision.EIGHTH, MidiCoreGrooveDrive.DRIVING)),
        )
        val restrainedProject = drivingProject.copy(
            arrangementPlan = planFor(MidiCoreSharedGrooveIntent(MidiCoreGrooveFeel.STRAIGHT, MidiCoreGrooveSubdivision.EIGHTH, MidiCoreGrooveDrive.RESTRAINED)),
        )
        val driving = MidiCoreDrumGenerator.generate(context(
            MidiCoreDrumGroovePatternId.DUSTY_STRAIGHT.id,
            project = drivingProject,
            acceptedDependencies = listOf(bassDependency),
        ))
        val restrained = MidiCoreDrumGenerator.generate(context(
            MidiCoreDrumGroovePatternId.DUSTY_STRAIGHT.id,
            project = restrainedProject,
            acceptedDependencies = listOf(bassDependency),
        ))
        val drivingKicks = driving.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>().filter { it.pitch == 36 }.map { it.startTick }
        val restrainedKicks = restrained.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>().filter { it.pitch == 36 }.map { it.startTick }

        assertTrue(driving.accepted, driving.validation.report.findings.toString())
        assertTrue(restrained.accepted, restrained.validation.report.findings.toString())
        assertTrue(240L in drivingKicks && 720L in drivingKicks)
        assertTrue(240L !in restrainedKicks && 720L !in restrainedKicks)
        assertEquals(driving, MidiCoreDrumGenerator.generate(context(
            MidiCoreDrumGroovePatternId.DUSTY_STRAIGHT.id,
            project = drivingProject,
            acceptedDependencies = listOf(bassDependency),
        )))
    }

    @Test
    fun `confirmed shared groove uses compound pulses on the six eight grid`() {
        val bassDependency = MidiCoreAcceptedDependencyContext(
            MidiCoreAcceptedDependency(CandidateRole.BASS, "verse-1", "bass-accepted", "e".repeat(64)),
            listOf(240L, 720L, 1_200L).map { start -> MidiCoreGenerationNote(start, start + 120, 36, 80) },
        )
        val project = project(
            endTick = 1_440,
            meter = ProjectMeter(6, 3),
            arrangementPlan = planFor(MidiCoreSharedGrooveIntent(
                MidiCoreGrooveFeel.STRAIGHT,
                MidiCoreGrooveSubdivision.EIGHTH,
                MidiCoreGrooveDrive.DRIVING,
            )),
        )
        val result = MidiCoreDrumGenerator.generate(context(
            MidiCoreDrumGroovePatternId.DUSTY_STRAIGHT.id,
            project = project,
            acceptedDependencies = listOf(bassDependency),
        ))
        val kicks = result.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>()
            .filter { it.pitch == 36 }
            .map { it.startTick }

        assertTrue(result.accepted, result.validation.report.findings.toString())
        assertTrue(240L in kicks && 1_200L in kicks)
        assertTrue(720L !in kicks, "The second dotted-quarter pulse is not an off-beat support position: $kicks")
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
    fun `steady planned groove preserves intro and outro support ceilings`() {
        val project = project(1_920, arrangementPlan = planFor(MidiCoreSharedGrooveIntent(
            MidiCoreGrooveFeel.STRAIGHT, MidiCoreGrooveSubdivision.EIGHTH, MidiCoreGrooveDrive.STEADY)))
        val bass = MidiCoreAcceptedDependencyContext(
            MidiCoreAcceptedDependency(CandidateRole.BASS, "verse-1", "bass-accepted", "1".repeat(64)),
            listOf(MidiCoreGenerationNote(720, 840, 36, 80)))
        for (purpose in listOf(MidiCoreSectionPurpose.INTRO, MidiCoreSectionPurpose.OUTRO)) {
            val context = context(MidiCoreDrumGroovePatternId.DUSTY_STRAIGHT.id,
                project = project, purpose = purpose, acceptedDependencies = listOf(bass))
            val withBass = MidiCoreDrumGenerator.generate(context)
            val authored = MidiCoreDrumGenerator.generate(context.copy(acceptedDependencies = emptyList()))
            assertTrue(withBass.accepted, withBass.validation.report.findings.toString())
            assertEquals(authored.candidate.events, withBass.candidate.events)
        }
    }

    @Test
    fun `intent advisory ignores restrained and ineligible compound pickups`() {
        for ((drive, onset) in listOf(MidiCoreGrooveDrive.RESTRAINED to 720L, MidiCoreGrooveDrive.DRIVING to 120L)) {
            val meter = if (drive == MidiCoreGrooveDrive.RESTRAINED) ProjectMeter(4, 2) else ProjectMeter(6, 3)
            val endTick = if (drive == MidiCoreGrooveDrive.RESTRAINED) 1_920L else 1_440L
            val project = project(endTick, meter = meter, arrangementPlan = planFor(
                MidiCoreSharedGrooveIntent(MidiCoreGrooveFeel.STRAIGHT, MidiCoreGrooveSubdivision.EIGHTH, drive)))
            val bass = MidiCoreAcceptedDependencyContext(
                MidiCoreAcceptedDependency(CandidateRole.BASS, "verse-1", "bass-accepted", "1".repeat(64)),
                listOf(MidiCoreGenerationNote(onset, onset + 120, 36, 80)))
            val context = context(MidiCoreDrumGroovePatternId.DUSTY_STRAIGHT.id,
                project = project, acceptedDependencies = listOf(bass))
            val report = MidiCoreRoleValidator.validate(context,
                listOf(MidiCoreCandidateEvent.Note(0, 120, 36, 80))).report
            assertTrue(report.findings.none { it.code == MidiCoreRoleFindingCode.KICK_BASS_INTENT_MISSING }, report.findings.toString())
        }
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
    fun `confirmed phrase pickup realizes a fill only at a two bar next-section transition`() {
        val project = transitionProject(
            firstBars = 2,
            secondBars = 1,
            firstPurpose = MidiCoreArrangementPurpose.PRE_CHORUS,
            secondPurpose = MidiCoreArrangementPurpose.CHORUS,
            firstPhrase = "pre-phrase",
            secondPhrase = "chorus-phrase",
            firstExit = MidiCoreBoundaryIntent.PICKUP,
            secondEntry = MidiCoreBoundaryIntent.PICKUP,
        )

        val result = MidiCoreDrumGenerator.generate(
            context(
                MidiCoreDrumGroovePatternId.DUSTY_STRAIGHT.id,
                project = project,
                purpose = MidiCoreSectionPurpose.UNSPECIFIED,
                fillPatternId = MidiCoreDrumFillPatternId.SOFT_TWO_STROKE.id,
            ),
        )
        val snareStarts = result.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>()
            .filter { it.pitch == 38 }.map { it.startTick }

        assertTrue(result.accepted, result.validation.report.findings.toString())
        assertTrue(3_600L in snareStarts && 3_720L in snareStarts, snareStarts.toString())
        assertTrue(result.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>().all { it.endTick <= 3_840L })
    }

    @Test
    fun `one bar harmony edge and quiet intro do not infer a style fill or bass pickup`() {
        val harmonyEdge = transitionProject(
            firstBars = 1,
            secondBars = 1,
            firstPurpose = MidiCoreArrangementPurpose.VERSE,
            secondPurpose = MidiCoreArrangementPurpose.VERSE,
            firstPhrase = "shared-phrase",
            secondPhrase = "shared-phrase",
            firstExit = MidiCoreBoundaryIntent.NONE,
            secondEntry = MidiCoreBoundaryIntent.NONE,
            firstSymbol = "C",
            secondSymbol = "F",
        )
        val quietIntro = transitionProject(
            firstBars = 1,
            secondBars = 1,
            firstPurpose = MidiCoreArrangementPurpose.INTRO,
            secondPurpose = MidiCoreArrangementPurpose.VERSE,
            firstPhrase = "intro-phrase",
            secondPhrase = "verse-phrase",
            firstExit = MidiCoreBoundaryIntent.PICKUP,
            secondEntry = MidiCoreBoundaryIntent.PICKUP,
        )
        val bass = MidiCoreAcceptedDependencyContext(
            MidiCoreAcceptedDependency(CandidateRole.BASS, "verse-1", "bass-pickup", "f".repeat(64)),
            listOf(MidiCoreGenerationNote(720, 840, 36, 80)),
        )

        val edge = MidiCoreDrumGenerator.generate(context(
            MidiCoreDrumGroovePatternId.DUSTY_STRAIGHT.id,
            project = harmonyEdge,
            fillPatternId = MidiCoreDrumFillPatternId.SOFT_TWO_STROKE.id,
        ))
        val intro = MidiCoreDrumGenerator.generate(context(
            MidiCoreDrumGroovePatternId.DUSTY_STRAIGHT.id,
            project = quietIntro,
            purpose = MidiCoreSectionPurpose.UNSPECIFIED,
            fillPatternId = MidiCoreDrumFillPatternId.SOFT_TWO_STROKE.id,
            acceptedDependencies = listOf(bass),
        ))
        val edgeSnares = edge.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>().filter { it.pitch == 38 }.map { it.startTick }
        val introKicks = intro.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>().filter { it.pitch == 36 }.map { it.startTick }

        assertTrue(edge.accepted && intro.accepted)
        assertFalse(1_680L in edgeSnares || 1_800L in edgeSnares, edgeSnares.toString())
        assertFalse(720L in introKicks, introKicks.toString())
    }

    @Test
    fun `pickup into quiet next intro or outro suppresses fills and final bar bass kicks`() {
        for (quietPurpose in listOf(MidiCoreArrangementPurpose.INTRO, MidiCoreArrangementPurpose.OUTRO)) {
            val project = transitionProject(
                firstBars = 2,
                secondBars = 1,
                firstPurpose = MidiCoreArrangementPurpose.VERSE,
                secondPurpose = quietPurpose,
                firstPhrase = "verse-phrase",
                secondPhrase = "quiet-phrase",
                firstExit = MidiCoreBoundaryIntent.PICKUP,
                secondEntry = MidiCoreBoundaryIntent.PICKUP,
            )
            val bass = MidiCoreAcceptedDependencyContext(
                MidiCoreAcceptedDependency(CandidateRole.BASS, "verse-1", "bass-quiet-$quietPurpose", "e".repeat(64)),
                listOf(MidiCoreGenerationNote(3_600, 3_720, 36, 80)),
            )

            val result = MidiCoreDrumGenerator.generate(context(
                MidiCoreDrumGroovePatternId.DUSTY_STRAIGHT.id,
                project = project,
                purpose = MidiCoreSectionPurpose.UNSPECIFIED,
                fillPatternId = MidiCoreDrumFillPatternId.SOFT_TWO_STROKE.id,
                acceptedDependencies = listOf(bass),
            ))
            val notes = result.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>()
            val snareStarts = notes.filter { it.pitch == 38 }.map { it.startTick }
            val kickStarts = notes.filter { it.pitch == 36 }.map { it.startTick }

            assertTrue(result.accepted, "$quietPurpose: ${result.validation.report.findings}")
            assertFalse(3_600L in snareStarts || 3_720L in snareStarts, "$quietPurpose: $snareStarts")
            assertFalse(3_600L in kickStarts, "$quietPurpose: $kickStarts")
        }
    }

    @Test
    fun `repeat family varies one complete compatible groove deterministically`() {
        val project = transitionProject(
            firstBars = 1,
            secondBars = 1,
            firstPurpose = MidiCoreArrangementPurpose.CHORUS,
            secondPurpose = MidiCoreArrangementPurpose.CHORUS,
            firstPhrase = "chorus-a",
            secondPhrase = "chorus-b",
            firstExit = MidiCoreBoundaryIntent.HOLD,
            secondEntry = MidiCoreBoundaryIntent.HOLD,
            repeatFamily = "chorus-family",
        )
        val first = MidiCoreDrumGenerator.generate(context(
            MidiCoreDrumGroovePatternId.DUSTY_STRAIGHT.id,
            project = project,
            purpose = MidiCoreSectionPurpose.UNSPECIFIED,
        ))
        val repeated = MidiCoreDrumGenerator.generate(context(
            MidiCoreDrumGroovePatternId.DUSTY_STRAIGHT.id,
            project = project,
            occurrenceId = "verse-2",
            purpose = MidiCoreSectionPurpose.UNSPECIFIED,
        ))

        assertTrue(first.accepted && repeated.accepted)
        assertNotEquals(
            first.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>().map { it.startTick to it.pitch },
            repeated.candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>()
                .map { it.startTick - 1_920L to it.pitch },
        )
        assertEquals(repeated, MidiCoreDrumGenerator.generate(context(
            MidiCoreDrumGroovePatternId.DUSTY_STRAIGHT.id,
            project = project,
            occurrenceId = "verse-2",
            purpose = MidiCoreSectionPurpose.UNSPECIFIED,
        )))
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
        occurrenceId: String = "verse-1",
        fillPatternId: String? = null,
        acceptedDependencies: List<MidiCoreAcceptedDependencyContext> = emptyList(),
    ): MidiCoreGenerationContext = MidiCoreGenerationContext.from(
        project = project,
        role = CandidateRole.DRUMS,
        occurrenceId = occurrenceId,
        performanceProfile = MidiCorePerformanceProfileCatalog.requireForRole(CandidateRole.DRUMS, profileId),
        patternId = patternId,
        generator = MidiCoreGeneratorInput("test-generator", "test-v1", patternId, seed),
        acceptedDependencies = acceptedDependencies,
        sectionPolicy = MidiCoreSectionPolicy(purpose, energy, density, fillPatternId),
    )

    private fun project(
        endTick: Long,
        meter: ProjectMeter = ProjectMeter(4, 2),
        arrangementPlan: MidiCoreArrangementPlan? = null,
    ): MidiCoreProject = MidiCoreProject(
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
            meter = meter,
            sectionDefinitions = listOf(ProjectSectionDefinition("verse", "Verse")),
            occurrences = listOf(ProjectSectionOccurrence("verse-1", "verse", "Verse", 0, endTick)),
            chordEvents = listOf(AuthoritativeChordEvent("verse-chord", "verse-1", "C", 0, endTick)),
        ),
        arrangementPlan = arrangementPlan,
    )

    private fun planFor(groove: MidiCoreSharedGrooveIntent): MidiCoreArrangementPlan = MidiCoreArrangementPlan(
        MidiCoreArrangementPlan.VERSION,
        groove,
        listOf(
            MidiCoreOccurrenceArrangementPlan(
                "verse-1", MidiCoreArrangementPurpose.VERSE, "phrase-1", "family-1", 1, 50,
                CandidateRole.entries.map { role -> MidiCoreRolePlanSettings(role, MidiCoreRoleActivity.SUPPORTING, 50, MidiCoreRegisterPreference.MID) },
                MidiCoreBoundaryIntent.NONE, MidiCoreBoundaryIntent.NONE,
            ),
        ),
    )

    private fun transitionProject(
        firstBars: Int,
        secondBars: Int,
        firstPurpose: MidiCoreArrangementPurpose,
        secondPurpose: MidiCoreArrangementPurpose,
        firstPhrase: String,
        secondPhrase: String,
        firstExit: MidiCoreBoundaryIntent,
        secondEntry: MidiCoreBoundaryIntent,
        firstSymbol: String = "C",
        secondSymbol: String = "C",
        repeatFamily: String? = null,
    ): MidiCoreProject {
        val firstEnd = firstBars * 1_920L
        val songEnd = firstEnd + secondBars * 1_920L
        val roleSettings = CandidateRole.entries.map { role ->
            MidiCoreRolePlanSettings(role, MidiCoreRoleActivity.SUPPORTING, 100, MidiCoreRegisterPreference.MID)
        }
        val firstFamily = repeatFamily ?: "first-family"
        val secondFamily = repeatFamily ?: "second-family"
        return project(songEnd).copy(
            authority = ProjectAuthority(
                key = ProjectKey(ProjectKeySpelling.C, ProjectScaleMode.MAJOR),
                tempo = ProjectTempo(500_000),
                meter = ProjectMeter(4, 2),
                sectionDefinitions = listOf(ProjectSectionDefinition("verse-a", "Verse A"), ProjectSectionDefinition("verse-b", "Verse B")),
                occurrences = listOf(
                    ProjectSectionOccurrence("verse-1", "verse-a", "Verse A", 0, firstEnd),
                    ProjectSectionOccurrence("verse-2", "verse-b", "Verse B", firstEnd, songEnd),
                ),
                chordEvents = listOf(
                    AuthoritativeChordEvent("chord-a", "verse-1", firstSymbol, 0, firstEnd),
                    AuthoritativeChordEvent("chord-b", "verse-2", secondSymbol, firstEnd, songEnd),
                ),
            ),
            arrangementPlan = MidiCoreArrangementPlan(
                MidiCoreArrangementPlan.VERSION,
                MidiCoreSharedGrooveIntent(MidiCoreGrooveFeel.STRAIGHT, MidiCoreGrooveSubdivision.EIGHTH, MidiCoreGrooveDrive.STEADY),
                listOf(
                    MidiCoreOccurrenceArrangementPlan(
                        "verse-1", firstPurpose, firstPhrase, firstFamily, 1, 50, roleSettings,
                        MidiCoreBoundaryIntent.NONE, firstExit,
                    ),
                    MidiCoreOccurrenceArrangementPlan(
                        "verse-2", secondPurpose, secondPhrase, secondFamily, if (repeatFamily == null) 1 else 2, 50, roleSettings,
                        secondEntry, MidiCoreBoundaryIntent.NONE,
                    ),
                ),
            ),
        )
    }

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
