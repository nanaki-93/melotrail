package app.melotrail.arrangement.core

import app.melotrail.midi.domain.MidiControlChangeEvent
import app.melotrail.midi.domain.MidiEventOrderingKey
import app.melotrail.midi.domain.MidiMelodySelection
import app.melotrail.midi.domain.MidiNoteEvent
import app.melotrail.midi.domain.MidiPitchBendEvent
import app.melotrail.midi.domain.MidiPpq
import app.melotrail.midi.domain.MidiProtectedMelodySelector
import app.melotrail.midi.domain.MidiProtectedMelodyView
import app.melotrail.midi.domain.MidiSemanticEventKind
import app.melotrail.midi.domain.MidiSourceEventIdentity
import app.melotrail.midi.domain.MidiSourceIdentity
import app.melotrail.midi.domain.SemanticMidiEvent
import app.melotrail.midi.domain.SemanticMidiSequence
import app.melotrail.midi.domain.SemanticMidiTrack
import app.melotrail.midi.domain.MidiTrackSummary
import app.melotrail.music.core.ProjectKeySpelling
import app.melotrail.music.core.ProjectMeter
import app.melotrail.music.core.ProjectScaleMode
import app.melotrail.music.core.ProjectTempo
import app.melotrail.project.AuthoritativeChordEvent
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

class MidiCoreMelodyHarmonyAnalysisTest {
    @Test
    fun `analyzes exact quarter beats at PPQ 25 without requiring sixteenth subdivisions`() {
        val view = protectedMelody(listOf(note(0, 0, 50, 61)), ppq = 25)
        val beforeEvents = view.events.toList()

        val report = analysis(view, listOf(chord("c", "C", 0, 100)))

        assertEquals(listOf(0L to 25L, 25L to 50L, 50L to 75L, 75L to 100L), report.beats.map { it.startTick to it.endTick })
        assertEquals(0L to 100L, report.bars.single().let { it.startTick to it.endTick })
        assertEquals(MidiCoreMelodyLocation(1, 1, 0, false), report.findings.single().location)
        assertEquals(MidiCoreHarmonyTensionCause.SUSTAINED_ACCENTED_TENSION, report.findings.single().cause)
        assertEquals(50L, report.findings.single().overlapTicks)
        assertEquals(beforeEvents, view.events)
    }

    @Test
    fun `weights held accented semitone tension above a short passing tone`() {
        val held = analysis(
            events = listOf(note(0, 0, 960, 61)),
            chords = listOf(chord("c", "C", 0, 1_920)),
        )
        val passing = analysis(
            events = listOf(note(0, 240, 480, 61)),
            chords = listOf(chord("c", "C", 0, 1_920)),
        )

        assertEquals(MidiCoreHarmonyTensionCause.SUSTAINED_ACCENTED_TENSION, held.findings.single().cause)
        assertEquals(MidiCoreHarmonyTensionConfidence.HIGH, held.findings.single().confidence)
        assertEquals(MidiCoreHarmonyTensionCause.PASSING_OR_NEIGHBOR_TONE, passing.findings.single().cause)
        assertEquals(MidiCoreHarmonyTensionConfidence.MEDIUM, passing.findings.single().confidence)
        assertTrue(held.findings.single().overlapTicks > passing.findings.single().overlapTicks)
    }

    @Test
    fun `weights an offbeat note by the strongest beat crossed during its overlap`() {
        val report = analysis(
            events = listOf(note(0, 240, 2_400, 61)),
            chords = listOf(chord("c", "C", 0, 3_840)),
        )

        val finding = report.findings.single()
        assertEquals(MidiCoreHarmonyTensionCause.SUSTAINED_ACCENTED_TENSION, finding.cause)
        assertEquals(240L, finding.startTick)
        assertEquals(2_400L, finding.endTick)
        assertEquals(1.0, finding.beatProminence)
        assertEquals(MidiCoreMelodyLocation(2, 1, 0, false), finding.location)
    }

    @Test
    fun `distinguishes the secondary 6 8 group accent from unaccented eighth notes`() {
        val report = analysis(
            events = listOf(
                note(0, 240, 480, 61),
                note(1, 480, 960, 63),
            ),
            chords = listOf(chord("c", "C", 0, 1_440)),
            meter = ProjectMeter(6, 3),
        )

        assertEquals(1.0, report.beats.single { it.location.beatNumber == 1 }.prominence)
        assertEquals(0.5, report.beats.single { it.location.beatNumber == 2 }.prominence)
        assertEquals(0.75, report.beats.single { it.location.beatNumber == 4 }.prominence)
        assertEquals(0.5, report.beats.single { it.location.beatNumber == 5 }.prominence)

        val weakFinding = report.findings.single { it.startTick == 240L }
        val secondaryAccentFinding = report.findings.single { it.startTick == 480L }
        assertEquals(MidiCoreHarmonyTensionCause.NON_CHORD_TONE, weakFinding.cause)
        assertEquals(0.5, weakFinding.beatProminence)
        assertEquals(MidiCoreHarmonyTensionCause.SUSTAINED_ACCENTED_TENSION, secondaryAccentFinding.cause)
        assertEquals(0.75, secondaryAccentFinding.beatProminence)
        assertEquals(MidiCoreMelodyLocation(1, 4, 0, false), secondaryAccentFinding.location)
    }

    @Test
    fun `keeps each pickup beat and partial final beat separate before the regular bar`() {
        val report = analysis(
            events = listOf(note(0, 480, 600, 60)),
            chords = listOf(chord("c", "C", 0, 3_840)),
            pickupTicks = 720,
        )

        val pickupBeats = report.beats.filter { it.location.pickup }
        assertEquals(listOf(0L to 480L, 480L to 720L), pickupBeats.map { it.startTick to it.endTick })
        assertEquals(listOf(1, 2), pickupBeats.map { it.location.beatNumber })
        assertEquals(listOf(MidiCoreMelodyRestSpan(0, 480)), pickupBeats.first().activity.rests)
        assertEquals(listOf(60), pickupBeats.last().activity.activeNotes.map { it.pitch })
        assertEquals(listOf(MidiCoreMelodyRestSpan(600, 720)), pickupBeats.last().activity.rests)
        assertEquals(MidiCoreMelodyLocation(1, 1, 0, false), report.beats.first { !it.location.pickup }.location)
        assertEquals(720L, report.beats.first { !it.location.pickup }.startTick)
        assertEquals(0L to 720L, report.bars.first().let { it.startTick to it.endTick })
    }

    @Test
    fun `keeps compound intervals suspensions and chromatic chords explainable without replacing harmony`() {
        val suspension = analysis(
            events = listOf(note(0, 0, 960, 60)),
            chords = listOf(chord("c", "C", 0, 480), chord("dm", "Dm", 480, 1_920)),
        )
        val chromatic = analysis(
            events = listOf(note(0, 0, 480, 61)),
            chords = listOf(chord("db", "Db", 0, 1_920)),
        )
        val compound = analysis(
            events = listOf(note(0, 0, 480, 86)),
            chords = listOf(chord("c", "C", 0, 1_920)),
        )

        assertEquals(MidiCoreHarmonyTensionCause.HELD_SUSPENSION, suspension.findings.single().cause)
        assertEquals("Db", chromatic.windows.single().chordSymbol)
        assertTrue(chromatic.findings.isEmpty(), "The chromatic Db chord remains authoritative and contains Db melody.")
        assertEquals(38, compound.findings.single().intervalAboveRootSemitones)
        assertEquals(2, compound.findings.single().nearestChordToneDistanceSemitones)
        val compoundWindow = compound.windows.single()
        assertEquals(86, compoundWindow.activity.register?.highestPitch)
    }

    @Test
    fun `derives pickup rest phrase repetition sustain and polyphony context without source mutation`() {
        val sourceEvents = listOf(
            control(0, 0, 64, 127),
            note(1, 0, 240, 61),
            note(2, 1_440, 1_680, 60),
            note(3, 1_680, 1_920, 62),
            note(4, 2_400, 2_640, 60),
            note(5, 2_640, 2_880, 62),
            note(6, 3_360, 3_600, 60),
            note(7, 3_360, 3_600, 67),
            control(8, 960, 64, 0),
        )
        val view = protectedMelody(sourceEvents)
        val before = view.events.toList()
        val report = analysis(view, listOf(chord("c", "C", 0, 3_840)), pickupTicks = 480)

        val firstWindow = report.windows.single()
        assertEquals(0, firstWindow.location.barNumber)
        assertTrue(firstWindow.activity.soundingNotes.any { it.keyReleaseTick == 240L && it.soundingEndTick == 960L })
        assertTrue(firstWindow.activity.soundingNotes.any { it.anchor })
        val sustainedOnlyBeat = report.beats.single { it.startTick == 480L }
        assertTrue(sustainedOnlyBeat.activity.soundingNotes.any { it.keyReleaseTick == 240L })
        assertTrue(sustainedOnlyBeat.activity.activeNotes.none { it.keyReleaseTick == 240L })
        assertTrue(firstWindow.accentedNoteIds.isNotEmpty())
        assertTrue(report.beats.first().activity.rests.isEmpty())
        assertTrue(report.bars.first().startTick == 0L && report.bars.first().endTick == 480L)
        assertTrue(report.phrases.any { it.repeatsPhraseIndex != null })
        assertTrue(firstWindow.activity.soundingNotes.count { it.startTick == 3_360L } == 2)
        assertEquals(before, view.events, "Analysis must not mutate protected source events.")
    }

    @Test
    fun `reports ambiguous bend limitations deterministically with no project writes`() {
        val events = listOf(note(0, 0, 960, 60), bend(1, 120, 512))
        val view = protectedMelody(events)
        val authority = authority(view, listOf(chord("c", "C", 0, 1_920)))
        val beforeEvents = view.events.toList()

        val first = MidiCoreMelodyHarmonyAnalyzer.analyze(authority, view)
        val second = MidiCoreMelodyHarmonyAnalyzer.analyze(authority, view)

        assertEquals(first, second)
        assertEquals(first.analysisSha256, second.analysisSha256)
        assertEquals(MidiCoreHarmonyTensionCause.PITCH_BEND_RANGE_UNKNOWN, first.findings.single().cause)
        assertEquals(MidiCoreHarmonyTensionConfidence.LIMITED, first.findings.single().confidence)
        assertFalse(first.findings.any { it.cause == MidiCoreHarmonyTensionCause.SUSTAINED_ACCENTED_TENSION })
        assertEquals(beforeEvents, view.events)
    }

    private fun analysis(
        events: List<SemanticMidiEvent>,
        chords: List<AuthoritativeChordEvent>,
        pickupTicks: Long = 0L,
        meter: ProjectMeter = ProjectMeter(4, 2),
    ): MidiCoreMelodyHarmonyAnalysis = analysis(protectedMelody(events), chords, pickupTicks, meter)

    private fun analysis(
        view: MidiProtectedMelodyView,
        chords: List<AuthoritativeChordEvent>,
        pickupTicks: Long = 0L,
        meter: ProjectMeter = ProjectMeter(4, 2),
    ): MidiCoreMelodyHarmonyAnalysis = MidiCoreMelodyHarmonyAnalyzer.analyze(authority(view, chords, pickupTicks, meter), view)

    private fun authority(
        view: MidiProtectedMelodyView,
        chords: List<AuthoritativeChordEvent>,
        pickupTicks: Long = 0L,
        meter: ProjectMeter = ProjectMeter(4, 2),
    ): MidiCoreAuthoritySnapshot {
        val end = chords.maxOf(AuthoritativeChordEvent::endTick)
        val project = MidiCoreProject(
            id = ProjectId("m02-analysis-project"),
            metadata = ProjectMetadata("M02 analysis", "2026-09-07T00:00:00Z"),
            sourceMidi = SourceMidiRecord(
                originalFilename = "melody.mid",
                sha256 = view.sourceSha256,
                format = 1,
                ppq = view.ppq.value,
                original = ProjectArtifact(ProjectRelativePath("source/original.mid"), view.sourceSha256),
                importReport = ProjectArtifact(ProjectRelativePath("reports/import.json"), "b".repeat(64)),
                trackSummaries = listOf(MidiTrackSummary(0, "Melody", emptyList())),
                sourceEndTick = end,
            ),
            selectedMelody = SelectedMelodyTrack(0, 0, view.identitySha256),
            authority = ProjectAuthority(
                key = ProjectKey(ProjectKeySpelling.C, ProjectScaleMode.MAJOR),
                tempo = ProjectTempo(500_000),
                meter = meter,
                sectionDefinitions = listOf(ProjectSectionDefinition("verse", "Verse")),
                occurrences = listOf(ProjectSectionOccurrence("verse-1", "verse", "Verse", 0, end)),
                chordEvents = chords,
                pickupTicks = pickupTicks,
            ),
        )
        return MidiCoreAuthoritySnapshot.from(project)
    }

    private fun protectedMelody(events: List<SemanticMidiEvent>, ppq: Int = 480): MidiProtectedMelodyView = MidiProtectedMelodySelector().select(
        SemanticMidiSequence(
            MidiSourceIdentity("a".repeat(64), "melody.mid", 1, MidiPpq(ppq)),
            listOf(SemanticMidiTrack(0, events)),
        ),
        MidiMelodySelection(0, 0),
    )

    private fun chord(id: String, symbol: String, start: Long, end: Long) =
        AuthoritativeChordEvent(id, "verse-1", symbol, start, end)

    private fun note(index: Int, start: Long, end: Long, pitch: Int) = MidiNoteEvent(
        key(start, MidiSemanticEventKind.NOTE, index), end, 0, pitch, 90,
    )

    private fun control(index: Int, tick: Long, controller: Int, value: Int) = MidiControlChangeEvent(
        key(tick, MidiSemanticEventKind.CONTROL_CHANGE, index), 0, controller, value,
    )

    private fun bend(index: Int, tick: Long, value: Int) = MidiPitchBendEvent(
        key(tick, MidiSemanticEventKind.PITCH_BEND, index), 0, value,
    )

    private fun key(tick: Long, kind: MidiSemanticEventKind, index: Int) =
        MidiEventOrderingKey(tick, kind, sourceEvent = MidiSourceEventIdentity(0, index))
}
