package app.melotrail.application

import app.melotrail.arrangement.PartAnalysis
import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.RenderFormat
import app.melotrail.arrangement.WorkflowArtifact
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompositionSettingsApplicationServiceTest {
    @TempDir lateinit var root: Path

    @Test
    fun `query preview update and reopen persist one deterministic settings decision`() {
        val service = service()
        service.create(CreateProjectRequest(root, "Draft", RenderFormat()))

        val initial = service.getCompositionSettings(GetCompositionSettings(root))
        assertTrue(initial.setupRequired)
        assertNull(initial.settings)
        assertTrue(initial.options.profileMeters.contains(TimeSignature(4, 4)))

        val input = settings("Night Walk", TimeSignature(7, 8))
        assertFalse(input.timeSignature in initial.options.profileMeters)
        val preview = service.previewSettingsChange(PreviewSettingsChange(root, 0, input))
        assertTrue(WorkflowArtifact.MIDI_FEEL in preview.invalidation.artifacts)
        assertTrue(WorkflowArtifact.GENERATED_MIDI in preview.invalidation.artifacts)

        val updated = service.updateCompositionSettings(UpdateCompositionSettings(root, 0, input))
        assertEquals(1, updated.settings.decisionRevision)
        assertEquals("Night Walk", updated.snapshot.name)
        assertTrue(updated.snapshot.readiness.compositionSettingsReady)
        assertTrue(updated.settings.decisionSha256.matches(Regex("[0-9a-f]{64}")))

        val reopened = service.getCompositionSettings(GetCompositionSettings(root))
        assertEquals(updated.settings, reopened.settings)
        assertFalse(reopened.setupRequired)
        assertEquals(updated.settings.decisionSha256, ProjectStore.read(root).envelope.compositionSettings?.decisionSha256)
    }

    @Test
    fun `invalid profile mood and stale revision are rejected before a project write`() {
        val service = service()
        service.create(CreateProjectRequest(root, "Draft", RenderFormat()))
        val input = settings("Draft")
        service.updateCompositionSettings(UpdateCompositionSettings(root, 0, input))
        val before = ProjectStore.read(root)

        assertFailsWith<IllegalArgumentException> {
            service.previewSettingsChange(PreviewSettingsChange(root, 1, input.copy(mood = MoodRef("not-in-profile", 1))))
        }
        assertFailsWith<IllegalArgumentException> {
            service.updateCompositionSettings(UpdateCompositionSettings(root, 0, input))
        }
        assertEquals(before, ProjectStore.read(root))
    }

    @Test
    fun `field-sensitive previews persist exact stale propagation and display name invalidates nothing`() {
        val service = service()
        service.create(CreateProjectRequest(root, "Draft", RenderFormat()))
        val original = settings("Draft")
        service.updateCompositionSettings(UpdateCompositionSettings(root, 0, original))
        val staleBeforeRename = service.open(root).readiness.staleArtifacts

        val nameOnly = service.updateCompositionSettings(UpdateCompositionSettings(root, 1, original.copy(name = "Renamed")))
        assertTrue(nameOnly.invalidation.artifacts.isEmpty())
        assertEquals(staleBeforeRename, nameOnly.snapshot.readiness.staleArtifacts)

        val tempo = service.updateCompositionSettings(UpdateCompositionSettings(root, 2, original.copy(name = "Renamed", tempo = Tempo(104.0))))
        assertEquals(
            setOf(WorkflowArtifact.ARRANGEMENT, WorkflowArtifact.GENERATED_MIDI, WorkflowArtifact.CORE_ARRANGEMENT, WorkflowArtifact.COHESION, WorkflowArtifact.CRITIC, WorkflowArtifact.FULL_SONG_ENHANCEMENT, WorkflowArtifact.HUMANIZATION, WorkflowArtifact.STEMS, WorkflowArtifact.DRY_MIX, WorkflowArtifact.MIX_REPORT,
                WorkflowArtifact.AUDIO_TEXTURE, WorkflowArtifact.MASTER, WorkflowArtifact.RELEASE, WorkflowArtifact.COMMERCIAL_EXPORT),
            tempo.invalidation.artifacts
        )

        val key = service.previewSettingsChange(PreviewSettingsChange(root, 3, original.copy(name = "Renamed", tempo = Tempo(104.0), key = MusicalKey(PitchClass.of(PitchSpelling.D), ScaleModeId.MAJOR))))
        assertTrue(WorkflowArtifact.GENERATED_MIDI in key.invalidation.artifacts)
        assertTrue(WorkflowArtifact.TRANSPOSED_MIDI in key.invalidation.artifacts)
        assertTrue(WorkflowArtifact.ANALYSIS in key.invalidation.artifacts)

        val mood = service.previewSettingsChange(PreviewSettingsChange(root, 3, original.copy(name = "Renamed", tempo = Tempo(104.0), mood = MoodRef("dark", 1))))
        assertTrue(WorkflowArtifact.MIDI_FEEL in mood.invalidation.artifacts)
        assertTrue(WorkflowArtifact.COHESION in mood.invalidation.artifacts)
        assertTrue(WorkflowArtifact.ANALYSIS in mood.invalidation.artifacts)
    }

    @Test
    fun `missing settings block creative derivation without blocking completed source inspection`() {
        val readyPart = PartSummary(
            "A", "", "source/A.mid", "A.mid", PartSourceType.MIDI,
            PartAnalysisSummary(PartAnalysisStatus.MIDI, "analysis/A.json"),
            PartPreparationSummary(
                sourcePreserved = true, inspected = true, preparedAudio = false, rawMidi = true, cleanMidi = true,
                analyzed = true, ready = true, warnings = emptyList(),
                midiQuality = MidiQualitySummary(MidiQualityStatus.CURRENT),
                midiFeel = MidiFeelSummary(), midiAiFix = MidiAiFixSummary()
            )
        )
        val snapshot = ProjectSnapshot(
            root, 4, "Draft", RenderFormat(), listOf(readyPart), emptyList(),
            ProjectReadiness(true, true, false, false, false, false, false, false, false, false, compositionSettingsReady = false)
        )

        val workflow = WorkflowReadModelDeriver.derive(snapshot)

        assertEquals(WorkflowAction.UPDATE_COMPOSITION_SETTINGS, workflow[WorkflowStage.PROJECT].nextAction)
        assertEquals(WorkflowState.COMPLETE, workflow[WorkflowStage.IMPORT_AND_INSPECTION].state)
        assertEquals(WorkflowPrerequisite.COMPOSITION_SETTINGS, workflow[WorkflowStage.ANALYSIS].prerequisite)
    }

    private fun settings(name: String, meter: TimeSignature = TimeSignature(4, 4)) = CompositionSettingsInput(
        name,
        MusicalKey(PitchClass.of(PitchSpelling.C), ScaleModeId.MAJOR),
        Tempo(90.0),
        meter,
        CompositionProfileRef("lofi", 1),
        MoodRef("warm", 1)
    )

    private fun service() = DefaultProjectApplicationService(
        midiPreparation = object : MidiPreparationService {
            override suspend fun transcribe(input: Path, output: Path) = Unit
            override suspend fun clean(input: Path, output: Path) = Unit
        },
    )
}
