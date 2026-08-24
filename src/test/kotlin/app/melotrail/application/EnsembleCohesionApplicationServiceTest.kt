package app.melotrail.application

import app.melotrail.arrangement.AnalysisKind
import app.melotrail.arrangement.BridgeType
import app.melotrail.arrangement.EnsembleCohesionModelIdentity
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
import app.melotrail.arrangement.EnsembleCohesionInput
import app.melotrail.arrangement.EnsembleTransitionContextFactory
import app.melotrail.arrangement.EnsembleCohesionPlan
import app.melotrail.arrangement.FullSongCriticReport
import app.melotrail.arrangement.FullSongAggregateMetric
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

class EnsembleCohesionApplicationServiceTest {
    @TempDir lateinit var root: Path

    @Test fun `cohesion receives project tempo instead of transcription tempo`() = runBlocking {
        project(listOf("A", "A", "A"))
        arrange()
        var captured: EnsembleCohesionInput? = null
        val service = DefaultEnsembleCohesionApplicationService { input ->
            captured = input
            plan(input)
        }

        service.generate(GenerateEnsembleCohesionRequest(root))

        val boundary = requireNotNull(captured).boundaries.first()
        assertEquals(90.0, boundary.outgoing.tempo.bpm)
        assertEquals(90.0, boundary.incoming.tempo.bpm)
    }

    @Test fun `boundary cohesion uses one aggregate approval`() = runBlocking {
        project(listOf("A", "A", "A"))
        arrange()
        val service = DefaultEnsembleCohesionApplicationService(::plan)
        val sourceBefore = java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(root.resolve("midi/clean/A.mid"))).joinToString("") { "%02x".format(it) }
        val draft = service.generate(GenerateEnsembleCohesionRequest(root, EnsembleCohesionPlannerKind.QWEN))
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

    @Test fun `Ensemble Cohesion never invokes its planner before arrangement is approved`() = runBlocking {
        project(listOf("A", "A"))
        var plannerCalled = false
        val service = DefaultEnsembleCohesionApplicationService { input ->
            plannerCalled = true
            plan(input)
        }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { service.generate(GenerateEnsembleCohesionRequest(root)) } }
        assertFalse(plannerCalled, "Ensemble Cohesion must remain after approved Arrangement")

        arrange()
        service.generate(GenerateEnsembleCohesionRequest(root)).boundaries.forEach { boundary ->
            service.reviewBoundary(root, boundary.outgoingInstanceId, boundary.incomingInstanceId)
        }
        service.approve(root)
        arrange()

        assertTrue(app.melotrail.arrangement.WorkflowArtifact.COHESION in ProjectStore.read(root).workflow.stale)
        assertFalse(service.generate(GenerateEnsembleCohesionRequest(root)).approved)
    }

    @Test fun `reordered structure invalidates draft identity and one occurrence needs no model plan`() = runBlocking {
        project(listOf("A", "A")); arrange(); val service = DefaultEnsembleCohesionApplicationService(::plan)
        val first = service.generate(GenerateEnsembleCohesionRequest(root))
        val project = ProjectStore.read(root); ProjectStore.write(root, project.copy(envelope = project.envelope.copy(structureOccurrences = project.envelope.structureOccurrences.take(1))))
        arrange()
        val single = service.generate(GenerateEnsembleCohesionRequest(root))
        assertFalse(first.inputHash == single.inputHash)
        assertTrue(single.boundaries.isEmpty())
        assertTrue(service.approve(root).approved)
    }

    @Test fun `rejected cohesion is stale evidence and generation retries from current input`() = runBlocking {
        project(listOf("A", "A")); arrange()
        val service = DefaultEnsembleCohesionApplicationService(::plan)
        service.generate(GenerateEnsembleCohesionRequest(root))
        service.reject(root)
        assertTrue(app.melotrail.arrangement.WorkflowArtifact.COHESION in ProjectStore.read(root).workflow.stale)
        assertFalse(service.regenerate(GenerateEnsembleCohesionRequest(root)).approved)
    }

    @Test fun `approval rejects a cohesion draft that raises deterministic blocker or critical counts`() {
        fun report(blocking: Double, critical: Double) = FullSongCriticReport.create(
            "a".repeat(64), "b".repeat(64), listOf(
                FullSongAggregateMetric("blockingIssueCount", blocking),
                FullSongAggregateMetric("criticalIssueCount", critical)
            ), emptyList(), emptyList()
        )

        assertThrows(IllegalArgumentException::class.java) { requireNoCohesionIssueIncrease(report(0.0, 0.0), report(1.0, 0.0)) }
        assertThrows(IllegalArgumentException::class.java) { requireNoCohesionIssueIncrease(report(0.0, 0.0), report(0.0, 1.0)) }
        requireNoCohesionIssueIncrease(report(1.0, 1.0), report(1.0, 1.0))
    }

    private fun plan(input: EnsembleCohesionInput): EnsembleCohesionPlan = EnsembleCohesionPlan(
        inputHash = input.inputHash, arrangementSha256 = input.arrangementSha256, contextSha256 = input.contextSha256,
        model = EnsembleCohesionModelIdentity("qwen", "1", "1".repeat(64)),
        boundaries = input.boundaries.map { b -> TransitionBridgePlan(
            outgoingInstanceId = b.outgoingInstanceId, incomingInstanceId = b.incomingInstanceId,
            outgoingHash = b.outgoing.sourceHash, incomingHash = b.incoming.sourceHash,
            arrangementSha256 = input.arrangementSha256, contextSha256 = input.contextSha256,
            roleAction = TransitionRoleAction.DRUM_FILL, bridgeType = BridgeType.DRUM_FILL, instrument = "drums",
            harmonicHandoff = HarmonicHandoff.HOLD, rhythmicGesture = RhythmicGesture.FILL,
            energyContour = EnergyContour.RISE, tempoHandoff = TimingHandoff.PRESERVE,
            meterHandoff = TimingHandoff.PRESERVE, rationale = "Carry energy into the next section"
        ) }
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
                harmony = HarmonySettings(progressions = listOf("verse", "chorus", "bridge").map { section ->
                    ChordProgression(
                        app.melotrail.harmony.SectionTypeId(section),
                        listOf(ChordEvent(ChordEventId("one"), PitchClass.of(PitchSpelling.C), ChordQuality.MAJOR, 0))
                    )
                }),
                structureOccurrences = structure.mapIndexed { index, partId -> StructureOccurrence("occ-$index", partId) }
            )
        ))
    }
    private suspend fun arrange() {
        approveSourceSongForArrangement(root)
        val service = DefaultArrangementApplicationService(libraryRoot = root)
        service.generate(
            GenerateArrangementRequest(root, instruments = listOf("piano", "drums"))
        )
        Files.createDirectories(root.resolve("midi/generated"))
        writeGeneratedDrums(root.resolve("midi/generated/drums.mid"), ProjectStore.read(root).envelope.structureOccurrences.size)
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
    /** Test baseline must be active in each occurrence to exercise local role derivation. */
    private fun writeGeneratedDrums(path: Path, occurrences: Int) {
        val sequence = Sequence(Sequence.PPQ, 480); val track = sequence.createTrack()
        repeat(occurrences) { index ->
            val start = index * 1_920L
            track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 9, 36, 90), start))
            track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 9, 36, 0), start + 960L))
        }
        MidiSystem.write(sequence, 1, path.toFile())
    }
}
