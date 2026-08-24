package app.melotrail.application

import app.melotrail.arrangement.CompositionSettings
import app.melotrail.arrangement.CriticWorkflowReferences
import app.melotrail.arrangement.DeterministicStemMixer
import app.melotrail.arrangement.DeterministicGlobalSongPlanner
import app.melotrail.arrangement.FullSongCriticReport
import app.melotrail.arrangement.FullSongEnhancementPlan
import app.melotrail.arrangement.GlobalSongPlanner
import app.melotrail.arrangement.InstrumentRenderer
import app.melotrail.arrangement.LogicalInstrument
import app.melotrail.arrangement.MixedStem
import app.melotrail.arrangement.MidiAnalysisStore
import app.melotrail.arrangement.MidiPartAnalyzer
import app.melotrail.arrangement.Project
import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.ProjectV4Envelope
import app.melotrail.arrangement.RenderFormat
import app.melotrail.arrangement.RenderResult
import app.melotrail.arrangement.SongPart
import app.melotrail.arrangement.SongPlan
import app.melotrail.arrangement.SongPlanningInput
import app.melotrail.arrangement.StructureOccurrence
import app.melotrail.arrangement.StemRenderReport
import app.melotrail.arrangement.WorkflowArtifactReference
import app.melotrail.arrangement.sha256
import app.melotrail.arrangement.canonicalMidiReferences
import app.melotrail.audio.AudioBuffer
import app.melotrail.audio.AudioFormat
import app.melotrail.harmony.ChordEvent
import app.melotrail.harmony.ChordEventId
import app.melotrail.harmony.ChordProgression
import app.melotrail.harmony.ChordQuality
import app.melotrail.harmony.HarmonySettings
import app.melotrail.harmony.SectionTypeId as HarmonySectionTypeId
import app.melotrail.arrangement.SectionTypeId
import app.melotrail.music.MusicalKey
import app.melotrail.music.PitchClass
import app.melotrail.music.PitchSpelling
import app.melotrail.music.ScaleModeId
import app.melotrail.music.Tempo
import app.melotrail.music.TimeSignature
import app.melotrail.profile.CompositionProfileRef
import app.melotrail.profile.MoodRef
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import javax.sound.midi.MidiEvent
import javax.sound.midi.MetaMessage
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The portable reference-song fixture is generated in code so its canonical
 * source, MIDI, plans, and derived artifacts are always inspectable.
 */
class ReferenceSongIntegrationTest {
    @TempDir lateinit var tempDir: Path

    @Test
    fun `reference song reaches critic no-op seeded humanization and render selection reproducibly`() = runBlocking {
        val first = runReference(tempDir.resolve("first"), FullSongPath.NO_OP)
        val relocated = runReference(tempDir.resolve("relocated/project"), FullSongPath.NO_OP)

        assertEquals(first.humanizedHashes, relocated.humanizedHashes)
        assertEquals(3, first.occurrences)
        assertEquals(2, first.boundaries)
        assertTrue(first.rendered)
    }

    @Test
    fun `reference song accepts explicit full-song bypass before seeded humanization`() = runBlocking {
        val result = runReference(tempDir.resolve("bypass"), FullSongPath.BYPASS)

        assertTrue(result.rendered)
        assertEquals(0, result.modelCalls)
    }

    @Test
    fun `reference song automatically rejects a no-change full-song candidate before seeded humanization`() = runBlocking {
        val result = runReference(tempDir.resolve("rejected"), FullSongPath.REJECTED)

        assertTrue(result.rendered)
        assertEquals(1, result.modelCalls)
    }

    private suspend fun runReference(root: Path, path: FullSongPath): RunResult {
        ReferenceSongFixture.create(root)
        val arrangements = DefaultArrangementApplicationService(
            deterministicGlobalPlanner = object : GlobalSongPlanner {
                override fun plan(input: SongPlanningInput): SongPlan {
                    val base = DeterministicGlobalSongPlanner().plan(input)
                    return app.melotrail.arrangement.SongPlanApplicationBinding.bind(base.copy(
                        sections = base.sections.map { section ->
                            section.copy(instrumentProgression = when (section.index) {
                                0 -> listOf("piano", "bass")
                                1 -> listOf("piano", "drums", "strings")
                                else -> listOf("piano", "pad")
                            })
                        }
                    ), input).also { it.requireValid(input) }
                }
            },
            libraryRoot = ReferenceSongFixture.library(root)
        )
        approveSourceSongForArrangement(root)
        arrangements.generate(GenerateArrangementRequest(root, instruments = LogicalInstrument.entries.map { it.wireName }))
        arrangements.generateRequiredMidi(root)
        arrangements.approveCoreArrangement(root)
        arrangements.generateOptionalMidi(root)
        generateApprovedCohesion(root, arrangements)
        val authority = MusicalAuthorityBuilder().wholeSongAnalysis(root)
        assertEquals(listOf("verse-1", "chorus-1", "verse-2"), authority.occurrences.map { it.occurrenceId })
        assertEquals(3, authority.harmony.map { it.occurrenceId }.distinct().size)
        val critic = DefaultFullSongCriticApplicationService().run(root)
        assertTrue(critic.current)
        assertEquals(critic.report.issues.map { it.id }, critic.issueLocations.map { it.issueId })
        assertTrue(critic.issueLocations.all { it.occurrenceId == null || it.startBar >= 0L })
        var modelCalls = 0
        val enhance = DefaultFullSongEnhancementApplicationService(planner = { input ->
            modelCalls++
            JSON.encodeToString(FullSongEnhancementPlan(
                inputSha256 = input.inputSha256,
                contextSha256 = input.contextSha256,
                criticInputSha256 = input.criticInputSha256,
                criticReportSha256 = input.criticReportSha256,
                modelIdentity = "reference-fake-130",
                operations = emptyList()
            ))
        })
        when (path) {
            FullSongPath.NO_OP -> {
                publishNoOpCriticReport(root, critic)
                assertEquals(app.melotrail.arrangement.FullSongEnhancementSelection.NO_OP, enhance.generateCandidate(root).selection)
            }
            FullSongPath.BYPASS -> {
                assertEquals(app.melotrail.arrangement.FullSongEnhancementSelection.BYPASS, enhance.selectBypass(root).selection)
            }
            FullSongPath.REJECTED -> {
                assertTrue(critic.report.issues.isNotEmpty(), "Reference fixture must supply a bounded fake-plan target.")
                val candidate = enhance.generateCandidate(root)
                assertEquals(app.melotrail.arrangement.FullSongEnhancementSelection.UNRESOLVED, candidate.selection)
                assertFalse(candidate.candidateAvailable)
                assertTrue(candidate.warnings.isNotEmpty())
                val evidence = requireNotNull(ProjectStore.read(root).workflow.fullSongEnhancement)
                assertTrue(evidence.afterCriticReport != null)
                assertEquals(app.melotrail.arrangement.FullSongEnhancementSelection.BYPASS, enhance.selectBypass(root).selection)
            }
        }

        val humanization = DefaultHumanizationApplicationService().generate(GenerateHumanizationRequest(root, seed = 130L))
        assertEquals(app.melotrail.arrangement.HumanizationSelection.HUMANIZED, humanization.selection)
        val project = ProjectStore.read(root)
        val hashes = requireNotNull(project.workflow.humanization).artifacts
            .associate { it.id to it.output.sha256 }
        assertEquals(setOf("bass", "drums", "pad", "strings"), hashes.keys)

        val renderer = FakeRenderer()
        arrangements.renderApprovedStems(root, renderer)
        assertEquals(LogicalInstrument.entries.toSet(), renderer.rendered)
        val approvedMelody = DefaultSourceSongCriticApplicationService().requireApprovedMelody(root)
        val renderedProject = ProjectStore.read(root)
        assertEquals(approvedMelody.connectedMidi.sha256, requireNotNull(renderedProject.workflow.coreArrangement).pianoSha256)
        assertTrue(requireNotNull(renderedProject.workflow.cohesion).occurrences.all { it.sourceSha256 == approvedMelody.connectedMidi.sha256 })
        val stemReport = JSON.decodeFromString(StemRenderReport.serializer(), Files.readString(root.resolve("stem-render.json")))
        assertEquals(approvedMelody.connectedMidi, stemReport.canonicalFullMelody)
        return RunResult(hashes, project.envelope.structureOccurrences.size, requireNotNull(project.workflow.cohesion).boundaries.size, renderer.rendered.isNotEmpty(), modelCalls)
    }

    private fun publishNoOpCriticReport(root: Path, critic: FullSongCriticSnapshot) {
        val noOp = FullSongCriticReport.create(
            critic.report.inputSha256,
            critic.report.contextSha256,
            critic.report.aggregateMetrics,
            emptyList(),
            emptyList()
        )
        Files.writeString(critic.artifact, JSON.encodeToString(noOp))
        val project = ProjectStore.read(root)
        val relative = root.relativize(critic.artifact).toString().replace('\\', '/')
        ProjectStore.write(root, project.copy(workflow = project.workflow.copy(
            critic = CriticWorkflowReferences(noOp.inputSha256, WorkflowArtifactReference(relative, sha256(critic.artifact)))
        )))
    }

    private enum class FullSongPath { NO_OP, BYPASS, REJECTED }
    private data class RunResult(val humanizedHashes: Map<String, String>, val occurrences: Int, val boundaries: Int, val rendered: Boolean, val modelCalls: Int)

    private class FakeRenderer : InstrumentRenderer {
        val rendered = linkedSetOf<LogicalInstrument>()

        override suspend fun render(midi: Path, instrument: LogicalInstrument, output: Path, format: RenderFormat, expectedFrames: Long): RenderResult {
            rendered += instrument
            val audio = AudioBuffer(
                FloatArray((expectedFrames * format.channels).toInt()) { 0.1f },
                AudioFormat(format.sampleRate, format.channels, 24, false, false, "WAV"),
                expectedFrames.toDouble() / format.sampleRate
            )
            DeterministicStemMixer().writeWav(MixedStem(audio, listOf(instrument.wireName)), output)
            return RenderResult(output, format.sampleRate, format.channels, 24, expectedFrames, audio.duration, 0.1, "fake", "1", "", "")
        }
    }

    private companion object {
        val JSON = Json { encodeDefaults = true }
    }
}

private object ReferenceSongFixture {
    fun library(root: Path): Path {
        val library = root.resolve("reference-sounds")
        val pitched = listOf("piano", "bass", "pad", "strings")
        pitched.forEach { name ->
            writeSample(library.resolve("$name/samples/$name.wav"))
            Files.createDirectories(library.resolve(name))
            Files.writeString(library.resolve("$name/$name.sfz"), "<region> sample=samples/$name.wav lokey=0 hikey=127")
        }
        val drums = listOf("kick" to 36, "snare" to 38, "clap" to 39, "hat_closed" to 42, "hat_open" to 46)
        drums.forEach { (name, _) -> writeSample(library.resolve("drums/samples/$name.wav")) }
        Files.createDirectories(library.resolve("drums"))
        Files.writeString(library.resolve("drums/drums.sfz"), drums.joinToString("\n") { (name, note) -> "<region> sample=samples/$name.wav key=$note" })
        Files.writeString(library.resolve("LICENSES.json"), """{"version":1,"libraries":{"fixture":{"displayName":"Reference Fixture","source":"generated","provenance":"generated-original","license":"CC0-1.0","commercialUse":true,"attributionRequired":false,"redistribution":"allowed"}}}""")
        Files.writeString(library.resolve("instruments.json"), """{"version":1,"workingSampleRate":44100,"midiChannelConvention":"one-based","instruments":{"piano":{"engine":"sfz","path":"piano/piano.sfz","licenseId":"fixture","midiProgram":0},"bass":{"engine":"sfz","path":"bass/bass.sfz","licenseId":"fixture","midiProgram":32},"drums":{"engine":"sfz","path":"drums/drums.sfz","licenseId":"fixture","midiChannel":10,"noteMap":{"kick":36,"snare":38,"clap":39,"closedHat":42,"openHat":46}},"pad":{"engine":"sfz","path":"pad/pad.sfz","licenseId":"fixture","midiProgram":89},"strings":{"engine":"sfz","path":"strings/strings.sfz","licenseId":"fixture","midiProgram":48}}}""")
        return library
    }

    fun create(root: Path) {
        writeMidi(root.resolve("source/melody.mid"))
        listOf("verse", "chorus").forEach { id ->
            Files.createDirectories(root.resolve("midi/raw")); Files.createDirectories(root.resolve("midi/clean"))
            Files.copy(root.resolve("source/melody.mid"), root.resolve("midi/raw/$id.mid"))
            Files.copy(root.resolve("source/melody.mid"), root.resolve("midi/clean/$id.mid"))
        }
        val settings = CompositionSettings(
            key = MusicalKey(PitchClass.of(PitchSpelling.A), ScaleModeId.NATURAL_MINOR), tempo = Tempo(96.0), timeSignature = TimeSignature(4, 4),
            profile = CompositionProfileRef("lofi", 1), mood = MoodRef("warm", 1), decisionRevision = 1,
            resolvedProfileSha256 = "a".repeat(64), decisionSha256 = "b".repeat(64)
        )
        val harmony = HarmonySettings(progressions = listOf(
            ChordProgression(HarmonySectionTypeId("verse"), listOf(ChordEvent(ChordEventId("a-minor"), PitchClass.of(PitchSpelling.A), ChordQuality.MINOR, 0))),
            ChordProgression(HarmonySectionTypeId("chorus"), listOf(ChordEvent(ChordEventId("f-major"), PitchClass.of(PitchSpelling.F), ChordQuality.MAJOR, 0))),
            ChordProgression(HarmonySectionTypeId("bridge"), listOf(ChordEvent(ChordEventId("e-major"), PitchClass.of(PitchSpelling.E), ChordQuality.MAJOR, 0)))
        ))
        ProjectStore.write(root, Project(
            name = "task-130-reference-song", renderFormat = RenderFormat(),
            parts = listOf(
                SongPart("verse", "source/melody.mid", name = "Reference melody", sectionType = SectionTypeId("verse"), midi = canonicalMidiReferences(root, "verse")),
                SongPart("chorus", "source/melody.mid", name = "Reference melody chorus", sectionType = SectionTypeId("chorus"), midi = canonicalMidiReferences(root, "chorus"))
            ),
            envelope = ProjectV4Envelope(
                compositionSettings = settings, harmony = harmony,
                structureOccurrences = listOf(StructureOccurrence("verse-1", "verse"), StructureOccurrence("chorus-1", "chorus"), StructureOccurrence("verse-2", "verse"))
            )
        ))
        listOf("verse", "chorus").forEach { id ->
            val project = ProjectStore.read(root)
            MidiAnalysisStore.write(root, project, id, MidiPartAnalyzer().analyze(root.resolve("midi/clean/$id.mid"), id))
        }
    }

    private fun writeMidi(path: Path) {
        Files.createDirectories(requireNotNull(path.parent))
        val sequence = Sequence(Sequence.PPQ, 480); val track = sequence.createTrack()
        val pitches = listOf(57, 60, 64, 60, 57, 60, 64, 69, 57, 60, 64, 60, 57, 60, 64, 69)
        pitches.forEachIndexed { index, pitch ->
            val start = index * 480L
            track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, pitch, 84), start))
            track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, pitch, 0), start + 360))
        }
        track.add(MidiEvent(MetaMessage(0x2F, byteArrayOf(), 0), 7_680))
        MidiSystem.write(sequence, 1, path.toFile())
    }

    private fun writeSample(path: Path) {
        Files.createDirectories(requireNotNull(path.parent))
        val data = byteArrayOf(0, 0)
        val bytes = ByteBuffer.allocate(44 + data.size).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray()).putInt(36 + data.size).put("WAVEfmt ".toByteArray()).putInt(16)
        bytes.putShort(1).putShort(1).putInt(44_100).putInt(88_200).putShort(2).putShort(16)
        bytes.put("data".toByteArray()).putInt(data.size).put(data)
        Files.write(path, bytes.array())
    }
}
