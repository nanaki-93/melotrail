package app.melotrail.application

import app.melotrail.arrangement.PartAnalysis
import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.RenderFormat
import app.melotrail.arrangement.WorkflowArtifact
import app.melotrail.harmony.ChordEvent
import app.melotrail.harmony.ChordEventId
import app.melotrail.harmony.ChordQuality
import app.melotrail.harmony.SectionTypeId
import app.melotrail.music.MusicalKey
import app.melotrail.music.PitchClass
import app.melotrail.music.PitchSpelling
import app.melotrail.music.ScaleModeId
import app.melotrail.music.Tempo
import app.melotrail.music.TimeSignature
import app.melotrail.profile.CompositionProfileRef
import app.melotrail.profile.MoodRef
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HarmonyApplicationServiceTest {
    @TempDir lateinit var root: Path

    @Test
    fun `Lo-fi setup seeds empty required slots and CRUD reorder use stable identities`() {
        val service = configuredService()
        val seeded = service.getHarmony(GetHarmony(root))
        assertEquals(1, seeded.projectRevision)
        assertEquals(1, seeded.revision)
        assertEquals(listOf(SectionTypeId.VERSE, SectionTypeId.CHORUS, SectionTypeId.BRIDGE), seeded.progressions.map { it.sectionType })
        assertFalse(seeded.completeness.complete)
        assertEquals(listOf(SectionTypeId.VERSE, SectionTypeId.CHORUS, SectionTypeId.BRIDGE), seeded.completeness.emptySections)

        val first = service.createHarmonyEvent(CreateHarmonyEvent(root, 1, 1, SectionTypeId.VERSE, chord("verse-one", PitchSpelling.C, ChordQuality.MAJOR)))
        val second = service.createHarmonyEvent(CreateHarmonyEvent(root, 1, 2, SectionTypeId.VERSE, chord("verse-two", PitchSpelling.G, ChordQuality.DOMINANT_7)))
        val reordered = service.reorderHarmonyEvent(ReorderHarmonyEvent(root, 1, 3, SectionTypeId.VERSE, ChordEventId("verse-two"), 0))
        val updated = service.updateHarmonyEvent(UpdateHarmonyEvent(root, 1, 4, SectionTypeId.VERSE, chord("verse-two", PitchSpelling.G, ChordQuality.MAJOR_7)))
        val deleted = service.deleteHarmonyEvent(DeleteHarmonyEvent(root, 1, 5, SectionTypeId.VERSE, ChordEventId("verse-one")))
        val future = service.createHarmonyEvent(CreateHarmonyEvent(root, 1, 6, SectionTypeId("outro"), chord("outro-one", PitchSpelling.A, ChordQuality.MINOR)))

        assertEquals(2, first.harmony.revision)
        assertEquals(3, second.harmony.revision)
        assertEquals(listOf("verse-two", "verse-one"), reordered.harmony.progressions.first { it.sectionType == SectionTypeId.VERSE }.events.map { it.id.value })
        assertEquals(ChordQuality.MAJOR_7, updated.harmony.progressions.first { it.sectionType == SectionTypeId.VERSE }.events.first().quality)
        assertEquals(listOf("verse-two"), deleted.harmony.progressions.first { it.sectionType == SectionTypeId.VERSE }.events.map { it.id.value })
        assertTrue(future.harmony.progressions.any { it.sectionType == SectionTypeId("outro") })
    }

    @Test
    fun `revision conflicts reject before write and harmony invalidation is exact`() {
        val service = configuredService()
        val event = chord("verse-one", PitchSpelling.D_FLAT, ChordQuality.MAJOR_9)
        val result = service.createHarmonyEvent(CreateHarmonyEvent(root, 1, 1, SectionTypeId.VERSE, event))
        val before = ProjectStore.read(root)

        assertFailsWith<IllegalArgumentException> {
            service.createHarmonyEvent(CreateHarmonyEvent(root, 0, 2, SectionTypeId.CHORUS, chord("chorus-one", PitchSpelling.C, ChordQuality.MAJOR)))
        }
        assertFailsWith<IllegalArgumentException> {
            service.createHarmonyEvent(CreateHarmonyEvent(root, 1, 1, SectionTypeId.CHORUS, chord("chorus-one", PitchSpelling.C, ChordQuality.MAJOR)))
        }
        assertEquals(before, ProjectStore.read(root))

        assertEquals(
            setOf(
                WorkflowArtifact.CORRECTED_MIDI, WorkflowArtifact.ENHANCED_MIDI, WorkflowArtifact.AI_FIX, WorkflowArtifact.MIDI_FEEL, WorkflowArtifact.COHESION,
                WorkflowArtifact.ARRANGEMENT, WorkflowArtifact.GENERATED_MIDI, WorkflowArtifact.HUMANIZATION, WorkflowArtifact.STEMS,
                WorkflowArtifact.DRY_MIX, WorkflowArtifact.AUDIO_TEXTURE, WorkflowArtifact.MASTER,
                WorkflowArtifact.RELEASE, WorkflowArtifact.COMMERCIAL_EXPORT
            ),
            result.invalidation.artifacts
        )
        assertFalse(WorkflowArtifact.CLEAN_MIDI in result.invalidation.artifacts)
        assertFalse(WorkflowArtifact.ANALYSIS in result.invalidation.artifacts)
    }

    @Test
    fun `section context persists reopens and preserves intentional chromatic harmony`() {
        val service = configuredService()
        val chromatic = chord("verse-db", PitchSpelling.D_FLAT, ChordQuality.MAJOR_9)
        service.createHarmonyEvent(CreateHarmonyEvent(root, 1, 1, SectionTypeId.VERSE, chromatic))
        service.createHarmonyEvent(CreateHarmonyEvent(root, 1, 2, SectionTypeId.CHORUS, chord("chorus-c", PitchSpelling.C, ChordQuality.MAJOR)))
        service.createHarmonyEvent(CreateHarmonyEvent(root, 1, 3, SectionTypeId.BRIDGE, chord("bridge-g", PitchSpelling.G, ChordQuality.MINOR)))

        val context = service.getHarmonySectionContext(GetHarmonySectionContext(root, SectionTypeId.VERSE))
        assertEquals(4, context.harmonyRevision)
        assertEquals(PitchSpelling.D_FLAT, context.progression.events.single().root.spelling)
        assertTrue(service.open(root).readiness.harmonyReady)

        val reopened = configuredService().getHarmonySectionContext(GetHarmonySectionContext(root, SectionTypeId.VERSE))
        assertEquals(context, reopened)
    }

    @Test
    fun `incomplete harmony blocks arrangement only after earlier stages are complete`() {
        val readyPart = PartSummary(
            "A", "", "source/A.mid", "A.mid", PartSourceType.MIDI,
            PartAnalysisSummary(PartAnalysisStatus.MIDI, "analysis/A.json"),
            PartPreparationSummary(
                sourcePreserved = true, inspected = true, preparedAudio = false, rawMidi = true, cleanMidi = true,
                analyzed = true, ready = true, warnings = emptyList(),
                midiQuality = MidiQualitySummary(MidiQualityStatus.CURRENT)
            )
        )
        val snapshot = ProjectSnapshot(
            root, 4, "Draft", RenderFormat(), listOf(readyPart), listOf(StructureSectionSummary(0, "A", 1, "A1", 1.0)),
            ProjectReadiness(
                cleanMidiReady = true, analysesReady = true, structureReady = true, songPlanAvailable = true,
                arrangementAvailable = false, generatedMidiAvailable = false, stemsAvailable = false,
                dryMixAvailable = false, loFiMixAvailable = false, masterAvailable = false,
                cohesionReady = true, compositionSettingsReady = true, harmonyReady = false
            )
        )

        val workflow = WorkflowReadModelDeriver.derive(snapshot)

        assertEquals(WorkflowState.COMPLETE, workflow[WorkflowStage.ANALYSIS].state)
        assertEquals(WorkflowAction.UPDATE_HARMONY, workflow[WorkflowStage.ARRANGEMENT].nextAction)
        assertEquals(WorkflowPrerequisite.COMPLETE_HARMONY, workflow[WorkflowStage.ARRANGEMENT].prerequisite)
    }

    private fun configuredService(): DefaultProjectApplicationService {
        val service = DefaultProjectApplicationService(
            midiPreparation = object : MidiPreparationService {
                override suspend fun transcribe(input: Path, output: Path) = Unit
                override suspend fun clean(input: Path, output: Path) = Unit
            },
            legacyPartAnalysis = LegacyPartAnalysisService { PartAnalysis(0.0, 44_100, 2, 0, 0.0, 0.0, true) }
        )
        if (!java.nio.file.Files.exists(root.resolve(ProjectStore.FILE_NAME))) {
            service.create(CreateProjectRequest(root, "Draft", RenderFormat()))
            service.updateCompositionSettings(UpdateCompositionSettings(root, 0, CompositionSettingsInput(
                "Draft", MusicalKey(PitchClass.of(PitchSpelling.C), ScaleModeId.MAJOR), Tempo(90.0), TimeSignature(4, 4),
                CompositionProfileRef("lofi", 1), MoodRef("warm", 1)
            )))
        }
        return service
    }

    private fun chord(id: String, spelling: PitchSpelling, quality: ChordQuality) =
        ChordEvent(ChordEventId(id), PitchClass.of(spelling), quality, 0)
}
