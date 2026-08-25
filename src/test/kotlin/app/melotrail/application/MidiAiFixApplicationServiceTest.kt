package app.melotrail.application

import app.melotrail.arrangement.LocalQwenClient
import app.melotrail.arrangement.JsonSchemaLocalQwenClient
import app.melotrail.arrangement.LocalQwenMidiAiFixPlanner
import app.melotrail.arrangement.MidiAiFixEdit
import app.melotrail.arrangement.MidiAiFixEditKind
import app.melotrail.arrangement.MidiAiFixModelIdentity
import app.melotrail.arrangement.MidiAiFixPlan
import app.melotrail.arrangement.MidiAiFixSelection
import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.MidiAnalysisStore
import app.melotrail.arrangement.MidiPartAnalyzer
import app.melotrail.arrangement.MidiAnalysis
import app.melotrail.arrangement.MidiKey
import app.melotrail.arrangement.CompositionSettings
import app.melotrail.arrangement.StructureOccurrence
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
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage

class MidiAiFixApplicationServiceTest {
    @TempDir lateinit var root: Path

    @Test
    fun `valid bounded draft preserves source evidence until explicit approval then can return to cleaned`() = runBlocking {
        val projectService = projectService()
        projectService.create(CreateProjectRequest(root))
        val source = writeMidi(root.resolveSibling("input.mid"))
        projectService.importPart(ImportPartRequest(root, "A", source))
        projectService.cleanMidi(CleanMidiRequest(root, "A", app.melotrail.arrangement.MidiCleanupOptions()))
        DefaultTechnicalCorrectionApplicationService().create(CreateTechnicalCorrectionRequest(root, "A"))
        configureCanonicalContext()
        val rawBefore = Files.readAllBytes(root.resolve("midi/raw/A.mid"))
        val cleanBefore = Files.readAllBytes(root.resolve("midi/clean/A.mid"))
        val sourceBefore = Files.readAllBytes(source)
        val service = DefaultMidiAiFixApplicationService(planner = { input ->
            MidiAiFixPlan(partId = input.partId, selectedInputSha256 = input.selectedInputSha256, inputHash = input.inputHash,
                contextSchemaVersion = input.contextSchemaVersion, contextSha256 = input.contextSha256,
                model = MidiAiFixModelIdentity("fake", "1", "1".repeat(64), "Apache-2.0"),
                edits = listOf(MidiAiFixEdit(MidiAiFixEditKind.VELOCITY, noteId = input.notes.first().id, velocity = input.notes.first().velocity - 10)))
        })

        val draft = service.create(CreateMidiAiFixRequest(root, "A"))

        assertFalse(draft.approved)
        assertTrue(draft.draftAvailable)
        assertEquals(MidiAiFixSelection.PENDING, ProjectStore.read(root).parts.single().midi!!.aiFixSelection)
        assertTrue(sourceBefore.contentEquals(Files.readAllBytes(source)))
        assertTrue(rawBefore.contentEquals(Files.readAllBytes(root.resolve("midi/raw/A.mid"))))
        assertTrue(cleanBefore.contentEquals(Files.readAllBytes(root.resolve("midi/clean/A.mid"))))
        assertTrue(Files.isRegularFile(root.resolve("midi/ai-fix/A/diff.json")))
        assertTrue(Files.isRegularFile(root.resolve("midi/ai-fix/A/audit.json")))
        assertTrue(Files.isRegularFile(root.resolve("midi/ai-fix/A/provenance.json")))
        assertTrue(Files.isRegularFile(root.resolve("midi/ai-fix/A/draft.comparison.json")))
        assertEquals(app.melotrail.arrangement.MidiMutationStage.AI_FIX,
            app.melotrail.arrangement.MidiAiFixStore.readDiff(root, "A").mutationReport.stage)

        val approved = service.approve(root, "A")
        assertTrue(approved.approved)
        assertTrue(Files.isRegularFile(root.resolve("midi/ai-fix/A/approved.comparison.json")))
        assertEquals(MidiAiFixSelection.APPROVED, ProjectStore.read(root).parts.single().midi!!.aiFixSelection)
        assertFalse(projectService.open(root).parts.single().preparation.analyzed)

        service.returnToCleaned(root, "A")
        assertEquals(MidiAiFixSelection.SKIP, ProjectStore.read(root).parts.single().midi!!.aiFixSelection)
        assertTrue(cleanBefore.contentEquals(Files.readAllBytes(root.resolve("midi/clean/A.mid"))))
    }

    @Test
    fun `no safe AI-fix plan keeps corrected MIDI selected without a draft`() = runBlocking {
        val projectService = projectService()
        projectService.create(CreateProjectRequest(root))
        val source = writeMidi(root.resolveSibling("no-safe-input.mid"))
        projectService.importPart(ImportPartRequest(root, "A", source))
        projectService.cleanMidi(CleanMidiRequest(root, "A", app.melotrail.arrangement.MidiCleanupOptions()))
        DefaultTechnicalCorrectionApplicationService().create(CreateTechnicalCorrectionRequest(root, "A"))
        configureCanonicalContext()
        val service = DefaultMidiAiFixApplicationService(planner = { input ->
            MidiAiFixPlan(
                partId = input.partId,
                selectedInputSha256 = input.selectedInputSha256,
                inputHash = input.inputHash,
                contextSchemaVersion = input.contextSchemaVersion,
                contextSha256 = input.contextSha256,
                model = MidiAiFixModelIdentity("fake", "1", "1".repeat(64), "Apache-2.0"),
                edits = emptyList()
            )
        })

        val result = service.create(CreateMidiAiFixRequest(root, "A"))
        val midi = ProjectStore.read(root).parts.single().midi!!

        assertTrue(result.noSafeFix)
        assertEquals("The model proposals would have created a same-pitch collision.", result.noSafeFixReason)
        assertEquals(null, result.outputSha256)
        assertFalse(result.draftAvailable)
        assertEquals(MidiAiFixSelection.SKIP, midi.aiFixSelection)
        assertEquals(null, midi.aiFix)
    }

    @Test
    fun `AI Fix can repair an imported part before song Structure is saved`() = runBlocking {
        val projectService = projectService()
        projectService.create(CreateProjectRequest(root))
        projectService.importPart(ImportPartRequest(root, "A", writeMidi(root.resolveSibling("pre-structure.mid"))))
        projectService.cleanMidi(CleanMidiRequest(root, "A", app.melotrail.arrangement.MidiCleanupOptions()))
        DefaultTechnicalCorrectionApplicationService().create(CreateTechnicalCorrectionRequest(root, "A"))
        var inputScope: app.melotrail.arrangement.MidiAiFixContextScope? = null
        val service = DefaultMidiAiFixApplicationService(planner = { input ->
            inputScope = input.contextScope
            MidiAiFixPlan(
                partId = input.partId,
                selectedInputSha256 = input.selectedInputSha256,
                inputHash = input.inputHash,
                contextSchemaVersion = input.contextSchemaVersion,
                contextSha256 = input.contextSha256,
                model = MidiAiFixModelIdentity("fake", "1", "1".repeat(64), "Apache-2.0"),
                edits = listOf(MidiAiFixEdit(MidiAiFixEditKind.VELOCITY, noteId = input.notes.first().id, velocity = input.notes.first().velocity - 10))
            )
        })

        val result = service.create(CreateMidiAiFixRequest(root, "A"))

        assertEquals(app.melotrail.arrangement.MidiAiFixContextScope.PART_LOCAL, inputScope)
        assertTrue(result.draftAvailable)
        assertTrue(ProjectStore.read(root).envelope.structureOccurrences.isEmpty())
    }

    @Test
    fun `AI Fix remains current when Technical Correction used a normalized baseline`() = runBlocking {
        val projectService = projectService()
        projectService.create(CreateProjectRequest(root))
        val source = writeMidi(root.resolveSibling("normalized-input.mid"))
        projectService.importPart(ImportPartRequest(root, "A", source))
        projectService.cleanMidi(CleanMidiRequest(root, "A", app.melotrail.arrangement.MidiCleanupOptions()))
        val normalized = root.resolve("midi/normalized/A.mid")
        val normalizationReport = app.melotrail.arrangement.MidiNormalizer().normalize(
            "A", root.resolve("midi/clean/A.mid"), normalized, app.melotrail.arrangement.MidiNormalizationConfig()
        )
        app.melotrail.arrangement.MidiNormalizationReportStore.write(root, "A", normalizationReport)
        val beforeCorrection = ProjectStore.read(root)
        ProjectStore.write(root, beforeCorrection.copy(parts = beforeCorrection.parts.map { part ->
            if (part.id == "A") part.copy(midi = part.midi!!.copy(
                normalized = "midi/normalized/A.mid", normalization = "midi/normalization/A.json"
            )) else part
        }))
        DefaultTechnicalCorrectionApplicationService().create(CreateTechnicalCorrectionRequest(root, "A"))
        configureCanonicalContext()
        val service = DefaultMidiAiFixApplicationService(planner = { input ->
            MidiAiFixPlan(
                partId = input.partId,
                selectedInputSha256 = input.selectedInputSha256,
                inputHash = input.inputHash,
                contextSchemaVersion = input.contextSchemaVersion,
                contextSha256 = input.contextSha256,
                model = MidiAiFixModelIdentity("fake", "1", "1".repeat(64), "Apache-2.0"),
                edits = listOf(MidiAiFixEdit(MidiAiFixEditKind.VELOCITY, noteId = input.notes.first().id, velocity = input.notes.first().velocity - 1))
            )
        })

        service.create(CreateMidiAiFixRequest(root, "A"))

        assertTrue(projectService.open(root).parts.single().preparation.midiAiFix.draftAvailable)
    }

    @Test
    fun `importing another part preserves an existing approved AI Fix`() = runBlocking {
        val projectService = projectService()
        projectService.create(CreateProjectRequest(root))
        val firstSource = writeMidi(root.resolveSibling("first-input.mid"))
        projectService.importPart(ImportPartRequest(root, "A", firstSource))
        projectService.cleanMidi(CleanMidiRequest(root, "A", app.melotrail.arrangement.MidiCleanupOptions()))
        DefaultTechnicalCorrectionApplicationService().create(CreateTechnicalCorrectionRequest(root, "A"))
        configureCanonicalContext()
        val aiFix = DefaultMidiAiFixApplicationService(planner = { input ->
            MidiAiFixPlan(
                partId = input.partId,
                selectedInputSha256 = input.selectedInputSha256,
                inputHash = input.inputHash,
                contextSchemaVersion = input.contextSchemaVersion,
                contextSha256 = input.contextSha256,
                model = MidiAiFixModelIdentity("fake", "1", "1".repeat(64), "Apache-2.0"),
                edits = listOf(MidiAiFixEdit(MidiAiFixEditKind.VELOCITY, noteId = input.notes.first().id, velocity = input.notes.first().velocity - 1))
            )
        })
        aiFix.create(CreateMidiAiFixRequest(root, "A"))
        aiFix.approve(root, "A")

        projectService.importPart(ImportPartRequest(root, "B", writeMidi(root.resolveSibling("second-input.mid"))))

        val snapshot = projectService.open(root)
        assertTrue(snapshot.parts.single { it.id == "A" }.preparation.midiAiFix.approvedAvailable)
    }

    @Test
    fun `model parser rejects unknown fields and unbounded edits while attaching canonical identity`() = runBlocking {
        val projectService = projectService(); projectService.create(CreateProjectRequest(root))
        val source = writeMidi(root.resolveSibling("input.mid")); projectService.importPart(ImportPartRequest(root, "A", source)); projectService.cleanMidi(CleanMidiRequest(root, "A", app.melotrail.arrangement.MidiCleanupOptions()))
        configureCanonicalContext()
        val input = aiFixInput()
        listOf(
            "{\"version\":2,\"partId\":\"A\",\"selectedInputSha256\":\"${input.selectedInputSha256}\",\"inputHash\":\"${input.inputHash}\",\"contextSchemaVersion\":${input.contextSchemaVersion},\"contextSha256\":\"${input.contextSha256}\",\"edits\":[],\"path\":\"/tmp/x\"}",
            "{\"version\":2,\"partId\":\"A\",\"selectedInputSha256\":\"${input.selectedInputSha256}\",\"inputHash\":\"${input.inputHash}\",\"contextSchemaVersion\":${input.contextSchemaVersion},\"contextSha256\":\"${input.contextSha256}\",\"edits\":[{\"kind\":\"velocity\",\"noteId\":\"${input.notes.first().id}\",\"velocity\":127}]}")
            .forEach { response -> assertThrows(IllegalArgumentException::class.java) { LocalQwenMidiAiFixPlanner(LocalQwenClient { _, _ -> response }).plan(input) } }
        val staleIdentity = LocalQwenMidiAiFixPlanner(LocalQwenClient { _, _ ->
            "{\"version\":0,\"partId\":\"other\",\"selectedInputSha256\":\"${"0".repeat(64)}\",\"inputHash\":\"${"0".repeat(64)}\",\"contextSchemaVersion\":0,\"contextSha256\":\"${"0".repeat(64)}\",\"edits\":[]}"
        }).plan(input)
        assertEquals(input.partId, staleIdentity.partId)
        assertEquals(input.selectedInputSha256, staleIdentity.selectedInputSha256)
        assertEquals(input.inputHash, staleIdentity.inputHash)
        assertEquals(input.contextSchemaVersion, staleIdentity.contextSchemaVersion)
        assertEquals(input.contextSha256, staleIdentity.contextSha256)
        assertFalse(Files.exists(root.resolve("midi/ai-fix/A/draft.mid")))
    }

    @Test
    fun `model response omits code-owned provenance and uses the concrete edit schema`() = runBlocking {
        val projectService = projectService(); projectService.create(CreateProjectRequest(root))
        val source = writeMidi(root.resolveSibling("input.mid")); projectService.importPart(ImportPartRequest(root, "A", source)); projectService.cleanMidi(CleanMidiRequest(root, "A", app.melotrail.arrangement.MidiCleanupOptions()))
        configureCanonicalContext()
        val input = aiFixInput()
        val trustedModel = MidiAiFixModelIdentity("qwen", "local", "a".repeat(64), "unknown")
        val response = "{\"version\":2,\"partId\":\"${input.partId}\",\"selectedInputSha256\":\"${input.selectedInputSha256}\",\"inputHash\":\"${input.inputHash}\",\"contextSchemaVersion\":${input.contextSchemaVersion},\"contextSha256\":\"${input.contextSha256}\",\"edits\":[{\"kind\":\"timing\",\"noteId\":\"${input.notes.first().id}\",\"startTick\":1}]}"

        val plan = LocalQwenMidiAiFixPlanner(LocalQwenClient { _, _ -> response }, trustedModel).plan(input)

        assertEquals(trustedModel, plan.model)
        assertEquals(MidiAiFixEditKind.TIMING, plan.edits.single().kind)
        assertEquals(1, plan.edits.single().startTick)
    }

    @Test
    fun `AI Fix asks schema-capable local clients for at most 32 edits`() = runBlocking {
        val projectService = projectService(); projectService.create(CreateProjectRequest(root))
        projectService.importPart(ImportPartRequest(root, "A", writeMidi(root.resolveSibling("schema-input.mid"))))
        projectService.cleanMidi(CleanMidiRequest(root, "A", app.melotrail.arrangement.MidiCleanupOptions()))
        configureCanonicalContext()
        val input = aiFixInput()
        var capturedSchema: JsonObject? = null
        val response = "{\"version\":2,\"partId\":\"${input.partId}\",\"selectedInputSha256\":\"${input.selectedInputSha256}\",\"inputHash\":\"${input.inputHash}\",\"contextSchemaVersion\":${input.contextSchemaVersion},\"contextSha256\":\"${input.contextSha256}\",\"edits\":[]}"
        val client = object : JsonSchemaLocalQwenClient {
            override fun complete(systemPrompt: String, userPrompt: String): String = error("AI Fix should use the JSON-schema capability")
            override fun completeJsonSchema(systemPrompt: String, userPrompt: String, schema: JsonObject): String {
                capturedSchema = schema
                return response
            }
        }

        LocalQwenMidiAiFixPlanner(client).plan(input)

        assertEquals(32, capturedSchema!!.getValue("properties").jsonObject.getValue("edits").jsonObject.getValue("maxItems").jsonPrimitive.int)
    }

    @Test
    fun `declared A minor stays authoritative over an inferred C major prompt observation`() = runBlocking {
        val projectService = projectService(); projectService.create(CreateProjectRequest(root))
        projectService.importPart(ImportPartRequest(root, "A", writeMidi(root.resolveSibling("authority.mid"))))
        projectService.cleanMidi(CleanMidiRequest(root, "A", app.melotrail.arrangement.MidiCleanupOptions()))
        configureCanonicalContext()
        val analysisPath = root.resolve("analysis/A.json")
        val analysis = Json.decodeFromString(MidiAnalysis.serializer(), Files.readString(analysisPath))
        Files.writeString(analysisPath, Json.encodeToString(MidiAnalysis.serializer(), analysis.copy(key = MidiKey("C", "major", 0.99))))
        val input = aiFixInput()
        val prompts = mutableListOf<String>()
        val response = "{\"version\":2,\"partId\":\"${input.partId}\",\"selectedInputSha256\":\"${input.selectedInputSha256}\",\"inputHash\":\"${input.inputHash}\",\"contextSchemaVersion\":${input.contextSchemaVersion},\"contextSha256\":\"${input.contextSha256}\",\"edits\":[]}"

        LocalQwenMidiAiFixPlanner(LocalQwenClient { _, prompt -> prompts += prompt; response }).plan(input)

        assertEquals("A natural minor", input.declaredKey!!.displayName)
        assertTrue(input.analyzedObservations.any { it.analyzedValue == "C major" })
        assertTrue(prompts.single().contains("\"declaredKey\"") && prompts.single().contains("\"contextSha256\"") && prompts.single().contains("\"limits\""))
    }

    private fun projectService() = DefaultProjectApplicationService(
        object : MidiPreparationService {
            override suspend fun transcribe(input: Path, output: Path) = Files.copy(input, output).let { Unit }
            override suspend fun clean(input: Path, output: Path) = Files.copy(input, output).let { Unit }
        }
    )

    private fun configureCanonicalContext() {
        val initial = ProjectStore.read(root)
        val part = initial.parts.single { it.id == "A" }
        val key = MusicalKey(PitchClass.of(PitchSpelling.A), ScaleModeId.NATURAL_MINOR)
        val settings = CompositionSettings(key = key, tempo = Tempo(120.0), timeSignature = TimeSignature(4, 4))
        val harmony = HarmonySettings(progressions = listOf(ChordProgression(
            app.melotrail.harmony.SectionTypeId(part.sectionType.value),
            listOf(ChordEvent(ChordEventId("a-minor"), PitchClass.of(PitchSpelling.A), ChordQuality.MINOR, 0))
        )))
        ProjectStore.write(root, initial.copy(envelope = initial.envelope.copy(
            compositionSettings = settings,
            harmony = harmony,
            structureOccurrences = listOf(StructureOccurrence("verse-1", "A"))
        )))
        val updated = ProjectStore.read(root)
        val midi = updated.parts.single { it.id == "A" }.midi!!
        val reference = if (midi.technicalCorrectionSelection == app.melotrail.arrangement.TechnicalCorrectionSelection.CORRECTED) {
            midi.technicalCorrection!!.output.file
        } else {
            midi.clean!!
        }
        MidiAnalysisStore.write(root, updated, "A", MidiPartAnalyzer().analyze(root.resolve(reference), "A"))
    }

    private fun aiFixInput(): app.melotrail.arrangement.MidiAiFixInput {
        val projection = MusicalAuthorityBuilder().partRepair(root, "A")
        return app.melotrail.arrangement.MidiAiFixInputFactory.build(projection = projection, selectedInput = root.resolve(projection.part.projectRelativePath))
    }

    private fun writeMidi(path: Path): Path {
        val sequence = Sequence(Sequence.PPQ, 480); val track = sequence.createTrack()
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, 60, 100), 0))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, 60, 0), 240))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, 64, 100), 480))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, 64, 0), 720))
        MidiSystem.write(sequence, 1, path.toFile()); return path
    }
}
