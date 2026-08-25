package app.melotrail.arrangement

import app.melotrail.application.MusicalAuthorityBuilder
import app.melotrail.harmony.ChordEvent
import app.melotrail.harmony.ChordEventId
import app.melotrail.harmony.ChordProgression
import app.melotrail.harmony.ChordQuality
import app.melotrail.harmony.HarmonySettings
import app.melotrail.harmony.SectionTypeId
import app.melotrail.music.MusicalKey
import app.melotrail.music.PitchClass
import app.melotrail.music.PitchSpelling
import app.melotrail.music.ScaleModeId
import app.melotrail.music.Tempo
import app.melotrail.music.TimeSignature
import app.melotrail.profile.CompositionProfileRef
import app.melotrail.profile.MoodRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import javax.sound.midi.MetaMessage
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage

class GeneratedRoleValidationTest {
    @TempDir lateinit var root: Path

    @Test
    fun `bass report is deterministic and accepts registered boundary values`() {
        val context = context("drums")
        writeMidi(context.midi, listOf(Note(0, 480, 36, 1), Note(480, 960, 36, 127)), channel = 9)

        val first = DeterministicGeneratedRoleValidator().validate(context.input())
        val second = DeterministicGeneratedRoleValidator().validate(context.input())

        assertTrue(first.passed, first.violations.joinToString("; "))
        assertEquals(first, second)
        assertEquals(listOf("arrangement", "authority", "grooveMap", "registry"), first.inputHashes.map { it.name })
        assertTrue(first.metrics.map { it.name }.containsAll(listOf("activeOccurrenceCount", "noteCount", "noteOnsetCount", "ppq")))
        assertEquals(36, first.kickTimingEvidence?.note)
        assertEquals(listOf("clap", "closedHat", "kick", "openHat", "snare"), first.instrumentMapEvidence?.notes?.map { it.name })
        assertEquals(listOf("occ-0"), first.target.occurrenceIds)
    }

    @Test
    fun `invalid note event and occurrence end produce a failed bounded report`() {
        val context = context()
        writeMidi(context.midi, listOf(Note(0, 480, 28, 100), Note(1_920, 1_921, 28, 100), Note(0, 480, 28, 100)))

        val report = DeterministicGeneratedRoleValidator().validate(context.input())

        assertFalse(report.passed)
        assertTrue(report.violations.any { "occurrence" in it || "duplicate" in it })
        assertEquals(report.violations.sorted(), report.violations)
        assertTrue(report.violations.size <= RoleValidationPolicy().maximumViolations)
    }

    @Test
    fun `drum validator checks the approved backbeat against accepted core state`() {
        val context = context("drums")
        writeMidi(context.midi, listOf(Note(0, 120, 36, 100)), channel = 9)
        val acceptedCore = ArrangementState.fromAcceptedPiano(
            480, listOf(MidiNote(0, 60, 90, 0, 1_920)), "c".repeat(64)
        )

        val report = DeterministicGeneratedRoleValidator().validate(context.input().copy(arrangementState = acceptedCore))

        assertFalse(report.passed)
        assertTrue(report.violations.contains("Drum backbeat does not match the approved beats 2 and 4 pattern"))
    }

    @Test
    fun `drum backbeats may share a nearby accepted piano onset within the approved groove residual`() {
        val context = context("drums")
        writeMidi(context.midi, listOf(Note(495, 600, 38, 100), Note(1_455, 1_560, 38, 100)), channel = 9)
        val piano = ArrangementState.fromAcceptedPiano(
            480,
            listOf(MidiNote(0, 60, 90, 495, 600), MidiNote(0, 64, 90, 1_455, 1_560)),
            "c".repeat(64)
        )

        val report = DeterministicGeneratedRoleValidator().validate(context.input().copy(arrangementState = piano))

        assertTrue(report.passed, report.violations.joinToString("; "))
    }

    @Test
    fun `drum fill may begin just before the nominal final beat within the approved groove residual`() {
        val context = context("drums")
        val drums = context.arrangement.sections.single().instruments.filterIsInstance<DrumsInstrumentPlan>().single()
        val arrangement = context.arrangement.copy(sections = context.arrangement.sections.map { section ->
            section.copy(instruments = section.instruments.map { instrument ->
                if (instrument is DrumsInstrumentPlan) drums.copy(
                    snarePattern = SnarePattern.NONE,
                    fillLastBar = true,
                    fillPlacement = DrumFillPlacement.LAST_BAR
                ) else instrument
            })
        })
        writeMidi(context.midi, listOf(Note(1_430, 1_500, 38, 90), Note(1_800, 1_860, 38, 96)), channel = 9)
        val piano = ArrangementState.fromAcceptedPiano(
            480, listOf(MidiNote(0, 60, 90, 0, 1_920)), "c".repeat(64)
        )

        val report = DeterministicGeneratedRoleValidator().validate(context.input().copy(
            arrangement = arrangement,
            arrangementState = piano
        ))

        assertTrue(report.passed, report.violations.joinToString("; "))
    }

    @Test
    fun `exact shared attack is not a flam when the piano has another nearby onset`() {
        val context = context("bass")
        writeMidi(context.midi, listOf(Note(15, 480, 48, 100)))
        val piano = ArrangementState.fromAcceptedPiano(
            480,
            listOf(MidiNote(0, 60, 90, 0, 120), MidiNote(0, 64, 90, 15, 480)),
            "c".repeat(64)
        )

        val report = DeterministicGeneratedRoleValidator().validate(context.input().copy(arrangementState = piano))

        assertTrue(report.passed, report.violations.joinToString("; "))
    }

    @Test
    fun `bass and drums reject off-grid timing and piano flams against the accepted groove map`() {
        val bass = context("bass")
        writeMidi(bass.midi, listOf(Note(48, 480, 40, 100)))
        val bassState = ArrangementState.fromAcceptedPiano(480, listOf(MidiNote(0, 64, 90, 0, 1_920)), "c".repeat(64))
        val bassReport = DeterministicGeneratedRoleValidator().validate(bass.input().copy(arrangementState = bassState))

        val drums = context("drums")
        writeMidi(drums.midi, listOf(Note(12, 120, 36, 100)), channel = 9)
        val drumState = ArrangementState.fromAcceptedPiano(480, listOf(MidiNote(0, 64, 90, 0, 1_920)), "c".repeat(64))
        val drumReport = DeterministicGeneratedRoleValidator().validate(drums.input().copy(arrangementState = drumState))

        assertTrue(bassReport.violations.contains("Generated role is off the approved groove-map phase"))
        assertTrue(drumReport.violations.contains("Generated role creates an audible piano flam"))
    }

    @Test
    fun `validator rejects chord clashes masking excess density and inactive-section notes`() {
        val bass = context("bass")
        writeMidi(bass.midi, listOf(Note(0, 480, 37, 100)))
        assertTrue(DeterministicGeneratedRoleValidator().validate(bass.input()).violations.any { it.contains("Bass note sounding") })

        val pad = context("pad")
        writeMidi(pad.midi, listOf(Note(0, 480, 64, 100)))
        val piano = ArrangementState.fromAcceptedPiano(480, listOf(MidiNote(0, 64, 90, 0, 1_920)), "c".repeat(64))
        assertTrue(DeterministicGeneratedRoleValidator().validate(pad.input().copy(arrangementState = piano)).violations.contains("Pad note masks the accepted piano register"))

        val drums = context("drums")
        writeMidi(drums.midi, (0 until 80).map { index -> Note(index * 20L, index * 20L + 10, if (index % 2 == 0) 36 else 42, 100) }, channel = 9)
        assertTrue(DeterministicGeneratedRoleValidator().validate(drums.input()).violations.any { it.contains("density") })

        val base = context("bass")
        val first = base.projection.occurrences.single()
        val inactive = first.copy(occurrenceId = "occ-1", startBar = 1, endBar = 2, startTick = 1_920, endTick = 3_840)
        writeMidi(base.midi, listOf(Note(1_920, 2_400, 40, 100)))
        val report = DeterministicGeneratedRoleValidator().validate(base.input().copy(
            arrangement = base.arrangement.copy(sections = base.arrangement.sections + DetailedArrangementSection(
                1, "occ-1", "A", SongSectionPurpose.DEVELOPMENT, 0.5, listOf(PianoSourcePlan()), TransitionPlan()
            )),
            projection = base.projection.copy(occurrences = listOf(first, inactive)),
            acceptedFullSongGrooveMap = grooveMap(listOf(first, inactive))
        ))
        assertTrue(report.violations.contains("Note occurs where role is not activated"))
    }

    @Test
    fun `explicit valid silence and failed candidate state admission remain distinct`() {
        val strings = context("strings", density = 0.0)
        writeMidi(strings.midi, emptyList())
        val report = DeterministicGeneratedRoleValidator().validate(strings.input().copy(deliberateSilence = true))
        val piano = ArrangementState.fromAcceptedPiano(480, listOf(MidiNote(0, 64, 90, 0, 1_920)), "c".repeat(64))

        assertTrue(report.passed, report.violations.joinToString("; "))
        assertFalse(piano.hasTrack("strings"))

        val failed = context("bass")
        writeMidi(failed.midi, listOf(Note(0, 480, 37, 100)))
        val failedReport = DeterministicGeneratedRoleValidator().validate(failed.input())
        val stateAfterAdmission = if (failedReport.passed) piano.acceptValidated("bass", failed.midi) else piano
        assertFalse(failedReport.passed)
        assertFalse(stateAfterAdmission.hasTrack("bass"))
    }

    @Test
    fun `pad and strings reject an unplanned cross-section octave reset`() {
        listOf("pad" to (48 to 64), "strings" to (55 to 72)).forEach { (role, pitches) ->
            val context = context(role)
            val first = context.projection.occurrences.single()
            val second = first.copy(occurrenceId = "occ-1", startBar = 1, endBar = 2, startTick = 1_920, endTick = 3_840)
            val plan = context.arrangement.sections.single().instruments.single { it.name == role }
            writeMidi(context.midi, listOf(Note(0, 480, pitches.first, 100), Note(1_920, 2_400, pitches.second, 100)))
            val report = DeterministicGeneratedRoleValidator().validate(context.input().copy(
                arrangement = context.arrangement.copy(sections = context.arrangement.sections + DetailedArrangementSection(
                    1, "occ-1", "A", SongSectionPurpose.DEVELOPMENT, 0.5, listOf(PianoSourcePlan(), plan), TransitionPlan()
                )),
                projection = context.projection.copy(occurrences = listOf(first, second))
            ))

            assertTrue(report.violations.contains("Sustained role has an avoidable cross-section octave jump"), "$role: ${report.violations}")
        }
    }

    @Test
    fun `approved Cohesion overlay window permits a transition crossing its occurrence boundary`() {
        val base = context()
        val first = base.projection.occurrences.single()
        val second = first.copy(occurrenceId = "occ-1", startBar = 1, endBar = 2, startTick = 1_920, endTick = 3_840)
        val projection = base.projection.copy(occurrences = listOf(first, second))
        val arrangement = DetailedArrangement(sections = listOf(
            DetailedArrangementSection(0, "occ-0", "A", SongSectionPurpose.DEVELOPMENT, 0.5, listOf(PianoSourcePlan()), TransitionPlan()),
            DetailedArrangementSection(1, "occ-1", "A", SongSectionPurpose.DEVELOPMENT, 0.5, listOf(PianoSourcePlan()), TransitionPlan())
        ))
        writeMidi(base.midi, listOf(Note(1_440, 2_160, 38, 100)), channel = 9)

        val withoutSuppliedWindow = DeterministicGeneratedRoleValidator().validate(
            GeneratedRoleValidationInput("transitions", base.midi, base.project, arrangement, projection, base.registry, "a".repeat(64), "b".repeat(64),
                transitionTimelineEndTick = 3_840)
        )
        val overlay = DeterministicGeneratedRoleValidator().validate(
            GeneratedRoleValidationInput("transitions", base.midi, base.project, arrangement, projection, base.registry, "a".repeat(64), "b".repeat(64),
                transitionWindows = listOf(TransitionMidiWindow(1_440, 2_400)), transitionTimelineEndTick = 3_840)
        )

        assertTrue(withoutSuppliedWindow.violations.contains("Transition note lies outside its supplied boundary window"))
        assertTrue(overlay.passed, overlay.violations.joinToString("; "))
    }

    private fun context(role: String = "bass", density: Double = 1.0): Context {
        val source = root.resolve("source/A.mid"); val clean = root.resolve("midi/clean/A.mid")
        writeMidi(source, listOf(Note(0, 1_920, 60, 100))); Files.createDirectories(clean.parent); Files.copy(source, clean, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        val project = Project(
            name = "validator", parts = listOf(Part("A", "source/A.mid", "verse", midi = canonicalMidiReferences(root, "A"))),
            renderFormat = RenderFormat(), envelope = ProjectV4Envelope(
                compositionSettings = CompositionSettings(
                    key = MusicalKey(PitchClass.of(PitchSpelling.E), ScaleModeId.MAJOR), tempo = Tempo(120.0), timeSignature = TimeSignature(4, 4),
                    profile = CompositionProfileRef("lofi", 1), mood = MoodRef("warm", 1), decisionRevision = 1,
                    resolvedProfileSha256 = "a".repeat(64), decisionSha256 = "b".repeat(64)
                ),
                harmony = HarmonySettings(progressions = listOf(ChordProgression(SectionTypeId("verse"), listOf(ChordEvent(ChordEventId("e"), PitchClass.of(PitchSpelling.E), ChordQuality.MAJOR, 0))))),
                structureOccurrences = listOf(StructureOccurrence("occ-0", "A"))
            )
        )
        ProjectStore.write(root, project)
        MidiAnalysisStore.write(root, project, "A", MidiPartAnalyzer().analyze(clean, "A"))
        val plan: DetailedInstrumentPlan = when (role) {
            "drums" -> DrumsInstrumentPlan(role = DrumsRole.MINIMAL, density = density, kickDensity = density, snarePattern = SnarePattern.BEATS_2_4, hiHatDensity = density, swing = 0.0, fillLastBar = false)
            "pad" -> PadInstrumentPlan(role = SustainedRole.SUSTAINED, density = density, register = MusicalRegister.MID)
            "strings" -> StringsInstrumentPlan(role = StringsRole.SUSTAINED_HARMONY, density = density, register = MusicalRegister.MID)
            else -> BassInstrumentPlan(role = DetailedBassRole.ROOT, density = density, movement = DetailedBassMovement.STATIC, register = MusicalRegister.LOW, syncopation = 0.0)
        }
        val arrangement = DetailedArrangement(sections = listOf(DetailedArrangementSection(0, "occ-0", "A", SongSectionPurpose.DEVELOPMENT, 0.5, listOf(PianoSourcePlan(), plan), TransitionPlan())))
        return Context(role, project, arrangement, MusicalAuthorityBuilder().arrangementGeneration(root), root.resolve("midi/generated/$role.mid"))
    }

    private fun writeMidi(path: Path, notes: List<Note>, channel: Int = 0) {
        Files.createDirectories(requireNotNull(path.parent)); val sequence = Sequence(Sequence.PPQ, 480); val track = sequence.createTrack()
        val micros = 500_000
        track.add(MidiEvent(MetaMessage(0x51, byteArrayOf((micros shr 16).toByte(), (micros shr 8).toByte(), micros.toByte()), 3), 0))
        track.add(MidiEvent(MetaMessage(0x58, byteArrayOf(4, 2, 24, 8), 4), 0))
        notes.forEach { note -> track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, channel, note.pitch, note.velocity), note.start)); track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, channel, note.pitch, 0), note.end)) }
        MidiSystem.write(sequence, 1, path.toFile())
    }

    private data class Context(val role: String, val project: Project, val arrangement: DetailedArrangement, val projection: app.melotrail.application.ArrangementGenerationProjection, val midi: Path) {
        val registry get() = InstrumentRegistryLoader(TestSoundLibrary.root()).load()
        fun input() = GeneratedRoleValidationInput(
            role, midi, project, arrangement, projection, registry, "a".repeat(64), "b".repeat(64),
            acceptedFullSongGrooveMap = if (role in setOf("bass", "drums")) grooveMap(projection.occurrences) else null
        )
    }
    private data class Note(val start: Long, val end: Long, val pitch: Int, val velocity: Int)

    private companion object {
        fun grooveMap(occurrences: List<app.melotrail.application.MusicalOccurrence>): FullSongGrooveMap = FullSongGrooveMap(
            ppq = 480, meterDenominator = 4, subdivisionsPerBeat = 4,
            points = occurrences.flatMap { occurrence ->
                (occurrence.startTick until occurrence.endTick step 120).map { tick ->
                    FullSongGroovePoint(occurrence.occurrenceId, (tick - occurrence.startTick) / 480, ((tick - occurrence.startTick) % 480 / 120).toInt(), tick, 0)
                }
            }.sortedBy(FullSongGroovePoint::globalTick),
            occurrenceTemplateFingerprints = occurrences.map { FullSongGrooveOccurrenceTemplate(it.occurrenceId, it.partId, "d".repeat(64)) },
            boundaries = emptyList(), maximumUnreviewedDiscontinuityTicks = 30
        )
    }
}
