package app.melotrail.application

import app.melotrail.arrangement.AnalysisKind
import app.melotrail.arrangement.BridgeType
import app.melotrail.arrangement.CohesionModelIdentity
import app.melotrail.arrangement.EnergyContour
import app.melotrail.arrangement.HarmonicHandoff
import app.melotrail.arrangement.LogicalInstrument
import app.melotrail.arrangement.MidiAnalysis
import app.melotrail.arrangement.MidiPartAnalyzer
import app.melotrail.arrangement.MidiReferences
import app.melotrail.arrangement.canonicalMidiReferences
import app.melotrail.arrangement.Part
import app.melotrail.arrangement.PartAnalysisReference
import app.melotrail.arrangement.Project
import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.ProjectV4Envelope
import app.melotrail.arrangement.CompositionSettings
import app.melotrail.arrangement.RenderFormat
import app.melotrail.arrangement.StructureOccurrence
import app.melotrail.arrangement.RhythmicGesture
import app.melotrail.arrangement.SectionInstance
import app.melotrail.arrangement.SongPlanningInput
import app.melotrail.arrangement.TimingHandoff
import app.melotrail.arrangement.TestSoundLibrary
import app.melotrail.arrangement.TransitionRoleAction
import app.melotrail.arrangement.GeneratedMidiArtifactReference
import app.melotrail.arrangement.GeneratedMidiWorkflowReferences
import app.melotrail.arrangement.WorkflowArtifact
import app.melotrail.arrangement.WorkflowArtifactReference
import app.melotrail.arrangement.WorkflowChange
import app.melotrail.arrangement.RoleValidationHash
import app.melotrail.arrangement.RoleValidationMetric
import app.melotrail.arrangement.RoleValidationReport
import app.melotrail.arrangement.RoleValidationTarget
import app.melotrail.arrangement.TransitionBridgePlan
import app.melotrail.arrangement.TransitionCohesionInput
import app.melotrail.arrangement.TransitionCohesionInputFactory
import app.melotrail.arrangement.TransitionCohesionPlan
import app.melotrail.harmony.ChordEvent
import app.melotrail.harmony.ChordEventId
import app.melotrail.harmony.ChordProgression
import app.melotrail.harmony.ChordQuality
import app.melotrail.harmony.HarmonySettings
import app.melotrail.music.MusicalKey
import app.melotrail.music.PitchClass
import app.melotrail.music.PitchSpelling
import app.melotrail.music.ScaleModeId
import app.melotrail.music.Tempo
import app.melotrail.music.TimeSignature
import app.melotrail.profile.CompositionProfileRef
import app.melotrail.profile.MoodRef
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
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

class CohesionApplicationServiceTest {
    @TempDir lateinit var root: Path

    @Test fun `boundary cohesion uses one aggregate approval`() = runBlocking {
        project(listOf("A", "A", "A"))
        arrange()
        val service = DefaultCohesionApplicationService(::plan)
        val sourceBefore = java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(root.resolve("midi/clean/A.mid"))).joinToString("") { "%02x".format(it) }
        val draft = service.generate(GenerateCohesionRequest(root, CohesionPlannerKind.QWEN))
        assertFalse(draft.approved)
        assertEquals(listOf("occ-0" to "occ-1", "occ-1" to "occ-2"), draft.boundaries.map { it.outgoingInstanceId to it.incomingInstanceId })
        assertTrue(draft.boundaries.all { Files.isRegularFile(it.bridgeMidi) && !it.reviewed })
        val reviewedHashes = ProjectStore.read(root).workflow.cohesion!!.let { workflow ->
            (workflow.occurrences.map { it.result } + workflow.roles.map { it.result }).associate { it.file to it.sha256 }
        }
        assertTrue(service.approve(root).approved)
        assertEquals(reviewedHashes, ProjectStore.read(root).workflow.cohesion!!.let { workflow ->
            (workflow.occurrences.map { it.result } + workflow.roles.map { it.result }).associate { it.file to it.sha256 }
        })
        assertEquals(sourceBefore, java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(root.resolve("midi/clean/A.mid"))).joinToString("") { "%02x".format(it) })
    }

    @Test fun `Cohesion rejects an absent or rerun arrangement`() = runBlocking {
        project(listOf("A", "A"))
        val service = DefaultCohesionApplicationService(::plan)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { service.generate(GenerateCohesionRequest(root)) } }

        arrange()
        service.generate(GenerateCohesionRequest(root)).boundaries.forEach { boundary ->
            service.reviewBoundary(root, boundary.outgoingInstanceId, boundary.incomingInstanceId)
        }
        service.approve(root)
        arrange()

        assertTrue(app.melotrail.arrangement.WorkflowArtifact.COHESION in ProjectStore.read(root).workflow.stale)
        assertFalse(service.generate(GenerateCohesionRequest(root)).approved)
    }

    @Test fun `reordered structure invalidates draft identity and one occurrence needs no model plan`() = runBlocking {
        project(listOf("A", "A")); arrange(); val service = DefaultCohesionApplicationService(::plan)
        val first = service.generate(GenerateCohesionRequest(root))
        val project = ProjectStore.read(root); ProjectStore.write(root, project.copy(envelope = project.envelope.copy(structureOccurrences = project.envelope.structureOccurrences.take(1))))
        arrange()
        val single = service.generate(GenerateCohesionRequest(root))
        assertFalse(first.inputHash == single.inputHash)
        assertTrue(single.boundaries.isEmpty())
        assertTrue(service.approve(root).approved)
    }

    @Test fun `rejected cohesion is stale evidence and generation retries from current input`() = runBlocking {
        project(listOf("A", "A")); arrange()
        val service = DefaultCohesionApplicationService(::plan)
        service.generate(GenerateCohesionRequest(root))
        service.reject(root)
        assertTrue(app.melotrail.arrangement.WorkflowArtifact.COHESION in ProjectStore.read(root).workflow.stale)
        assertFalse(service.regenerate(GenerateCohesionRequest(root)).approved)
    }

    private fun plan(input: TransitionCohesionInput): TransitionCohesionPlan = TransitionCohesionPlan(
        inputHash = input.inputHash, arrangementSha256 = input.arrangementSha256, contextSha256 = input.contextSha256,
        model = CohesionModelIdentity("qwen", "1", "1".repeat(64)),
        boundaries = input.boundaries.map { b -> TransitionBridgePlan(b.outgoingInstanceId, b.incomingInstanceId, b.outgoing.sourceHash, b.incoming.sourceHash, input.arrangementSha256, input.contextSha256, TransitionRoleAction.DRUM_FILL, BridgeType.DRUM_FILL, 1, "drums", HarmonicHandoff.HOLD, RhythmicGesture.FILL, EnergyContour.RISE, TimingHandoff.PRESERVE, TimingHandoff.PRESERVE, "Carry energy into the next section") }
    )

    private fun project(structure: List<String>) {
        Files.createDirectories(root.resolve("source")); Files.createDirectories(root.resolve("midi/clean")); Files.createDirectories(root.resolve("analysis"))
        Files.copy(TestSoundLibrary.root().resolve("instruments.json"), root.resolve("instruments.json"))
        writeMidi(root.resolve("source/A.mid")); Files.copy(root.resolve("source/A.mid"), root.resolve("midi/clean/A.mid"))
        val analysis = MidiPartAnalyzer().analyze(root.resolve("midi/clean/A.mid"), "A")
        Files.writeString(root.resolve("analysis/A.midi.json"), Json.encodeToString(MidiAnalysis.serializer(), analysis))
        ProjectStore.write(root, Project(
            name = "cohesion",
            parts = listOf(Part("A", "source/A.mid", analysis = PartAnalysisReference("analysis/A.midi.json", AnalysisKind.MIDI), midi = canonicalMidiReferences(root, "A"))),
            renderFormat = RenderFormat(),
            envelope = ProjectV4Envelope(
                compositionSettings = CompositionSettings(
                    key = MusicalKey(PitchClass.of(PitchSpelling.C), ScaleModeId.MAJOR), tempo = Tempo(90.0),
                    timeSignature = TimeSignature(4, 4), profile = CompositionProfileRef("lofi", 1), mood = MoodRef("warm", 1),
                    decisionRevision = 1, resolvedProfileSha256 = "a".repeat(64), decisionSha256 = "b".repeat(64)
                ),
                harmony = HarmonySettings(progressions = listOf(ChordProgression(
                    app.melotrail.harmony.SectionTypeId("verse"), listOf(ChordEvent(ChordEventId("one"), PitchClass.of(PitchSpelling.C), ChordQuality.MAJOR, 0))
                ))),
                structureOccurrences = structure.mapIndexed { index, partId -> StructureOccurrence("occ-$index", partId) }
            )
        ))
    }
    private suspend fun arrange() {
        val service = DefaultArrangementApplicationService(libraryRoot = root)
        service.generate(
            GenerateArrangementRequest(root, instruments = listOf("piano", "drums"))
        )
        Files.createDirectories(root.resolve("midi/generated"))
        Files.copy(root.resolve("midi/clean/A.mid"), root.resolve("midi/generated/drums.mid"), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        val project = ProjectStore.read(root)
        val hash = app.melotrail.arrangement.sha256(root.resolve("midi/generated/drums.mid"))
        val approval = requireNotNull(project.workflow.arrangement)
        Files.writeString(root.resolve("midi/generated/drums.validation.json"), Json.encodeToString(RoleValidationReport(
            role = "drums", target = RoleValidationTarget(occurrenceIds = emptyList()),
            inputHashes = listOf(RoleValidationHash("arrangement", approval.arrangement.sha256), RoleValidationHash("authority", approval.authoritySha256), RoleValidationHash("registry", approval.registrySha256)),
            outputSha256 = hash, policyVersion = 1, metrics = listOf(RoleValidationMetric("noteCount", 1), RoleValidationMetric("ppq", 480)),
            warnings = emptyList(), violations = emptyList(), passed = true
        )))
        val reportHash = app.melotrail.arrangement.sha256(root.resolve("midi/generated/drums.validation.json"))
        ProjectStore.write(root, project.copy(workflow = project.workflow.invalidate(WorkflowChange.GENERATED_MIDI)
            .markCurrent(WorkflowArtifact.GENERATED_MIDI)
            .copy(generatedMidi = GeneratedMidiWorkflowReferences(
                approval.arrangement.sha256, approval.authoritySha256, approval.registrySha256, "arrangement-generators-v1", 0L,
                listOf(GeneratedMidiArtifactReference("drums", WorkflowArtifactReference("midi/generated/drums.mid", hash),
                    WorkflowArtifactReference("midi/generated/drums.validation.json", reportHash)))
            ))))
    }
    private fun writeMidi(path: Path) { val sequence = Sequence(Sequence.PPQ, 480); val track = sequence.createTrack(); track.add(MidiEvent(MetaMessage(0x51, byteArrayOf(7, -95, 32), 3), 0)); track.add(MidiEvent(MetaMessage(0x58, byteArrayOf(4, 2, 24, 8), 4), 0)); track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, 60, 90), 0)); track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, 60, 0), 1_920)); MidiSystem.write(sequence, 1, path.toFile()) }
}
