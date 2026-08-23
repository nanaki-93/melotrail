package app.melotrail.application

import app.melotrail.arrangement.MidiAnalysis
import app.melotrail.arrangement.MidiAnalysisStore
import app.melotrail.arrangement.MidiPartAnalyzer
import app.melotrail.arrangement.MidiReferences
import app.melotrail.arrangement.canonicalMidiReferences
import app.melotrail.arrangement.MidiTempoChange
import app.melotrail.arrangement.MidiTimeSignature
import app.melotrail.arrangement.Part
import app.melotrail.arrangement.Project
import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.ProjectV4Envelope
import app.melotrail.arrangement.DeterministicStemMixer
import app.melotrail.arrangement.InstrumentRenderer
import app.melotrail.arrangement.LogicalInstrument
import app.melotrail.arrangement.MixedStem
import app.melotrail.arrangement.CompositionSettings
import app.melotrail.arrangement.RenderFormat
import app.melotrail.arrangement.RenderResult
import app.melotrail.arrangement.StructureOccurrence
import app.melotrail.arrangement.TestSoundLibrary
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
import kotlinx.serialization.decodeFromString
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import app.melotrail.audio.AudioBuffer
import app.melotrail.audio.AudioFormat
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage

class ArrangementApplicationServiceTest {
    @TempDir lateinit var tempDir: Path

    @Test
    fun `arrangement is blocked until the current source song is explicitly approved`() = runBlocking {
        val root = project("source-approval")
        val service = DefaultArrangementApplicationService(libraryRoot = TestSoundLibrary.root())

        val blocked = assertThrows(ApplicationServiceException::class.java) {
            runBlocking { service.generate(GenerateArrangementRequest(root, instruments = listOf("piano"))) }
        }
        assertTrue(blocked.message!!.contains("Source Song Critic"))

        approveSourceSongForArrangement(root)
        assertTrue(service.generate(GenerateArrangementRequest(root, instruments = listOf("piano"))).approved)
    }

    @Test
    fun `deterministic generation writes an approved inspectable arrangement snapshot`() = runBlocking {
        val root = project("approved")
        approveSourceSongForArrangement(root)
        val result = DefaultArrangementApplicationService(libraryRoot = TestSoundLibrary.root()).generate(GenerateArrangementRequest(root, instruments = listOf("piano", "bass")))

        assertTrue(Files.isRegularFile(root.resolve("song_plan.json")))
        assertTrue(Files.isRegularFile(root.resolve("section_variations.json")))
        assertTrue(Files.isRegularFile(root.resolve("arrangement_plan.json")))
        assertTrue(result.approved)
        assertFalse(result.approvalRequired)
        assertFalse(result.stale)
        assertTrue(result.sections.single().instruments.any { it.name == "piano" })
    }

    @Test
    fun `Qwen mode always creates a draft that requires explicit approval`() = runBlocking {
        val root = project("draft")
        approveSourceSongForArrangement(root)
        val service = DefaultArrangementApplicationService(
            deterministicGlobalPlanner = app.melotrail.arrangement.DeterministicGlobalSongPlanner(),
            qwenGlobalPlanner = app.melotrail.arrangement.DeterministicGlobalSongPlanner(),
            deterministicDetailedPlanner = app.melotrail.arrangement.DeterministicDetailedArrangementPlanner(),
            qwenDetailedPlanner = app.melotrail.arrangement.DeterministicDetailedArrangementPlanner(),
            libraryRoot = TestSoundLibrary.root()
        )

        val draft = service.generate(GenerateArrangementRequest(root, ArrangementPlannerKind.QWEN, instruments = listOf("piano")))
        assertTrue(draft.approvalRequired)
        assertFalse(draft.approved)
        assertTrue(Files.isRegularFile(root.resolve("arrangement_plan.draft.json")))
        assertFalse(Files.isRegularFile(root.resolve("arrangement_plan.json")))

        val approved = service.approve(root)
        assertTrue(approved.approved)
        assertTrue(Files.isRegularFile(root.resolve("arrangement_plan.json")))
    }

    @Test
    fun `arrangements complete without Cohesion and publish exact approval context`() = runBlocking {
        val root = project("cohesion-boundary", structure = listOf("A", "A"))
        approveSourceSongForArrangement(root)

        DefaultArrangementApplicationService(libraryRoot = TestSoundLibrary.root())
            .generate(GenerateArrangementRequest(root, instruments = listOf("piano", "drums")))

        val arrangement = kotlinx.serialization.json.Json { ignoreUnknownKeys = false }
            .decodeFromString(app.melotrail.arrangement.DetailedArrangement.serializer(), Files.readString(root.resolve("arrangement_plan.json")))
        val approval = ProjectStore.read(root).workflow.arrangement!!

        assertEquals(null, arrangement.cohesion)
        assertEquals(sha256(root.resolve("arrangement_plan.json")), approval.arrangement.sha256)
        assertTrue(approval.structureSha256.matches(Regex("[0-9a-f]{64}")))
        assertTrue(approval.occurrenceSha256.matches(Regex("[0-9a-f]{64}")))
        assertTrue(approval.contextSha256.matches(Regex("[0-9a-f]{64}")))
        assertTrue(approval.planSha256.matches(Regex("[0-9a-f]{64}")))
        assertTrue(arrangement.sections.first().transitionOut.type in setOf(app.melotrail.arrangement.TransitionType.BRIDGE, app.melotrail.arrangement.TransitionType.CROSSFADE))
    }

    @Test
    fun `validated piano bass drums and pad render a core draft without strings`() = runBlocking {
        val root = project("core-draft", structure = listOf("A", "A"))
        approveSourceSongForArrangement(root)
        val library = coreLibrary(root)
        val service = DefaultArrangementApplicationService(
            deterministicGlobalPlanner = object : app.melotrail.arrangement.GlobalSongPlanner {
                override fun plan(input: app.melotrail.arrangement.SongPlanningInput): app.melotrail.arrangement.SongPlan =
                    app.melotrail.arrangement.DeterministicGlobalSongPlanner().plan(input).let { plan ->
                        plan.copy(sections = plan.sections.map { section ->
                            section.copy(instrumentProgression = if (section.index == 0) listOf("piano", "bass", "drums") else listOf("piano", "bass", "pad"))
                        })
                            .also { it.requireValid(input) }
                    }
            },
            libraryRoot = library
        )

        service.generate(GenerateArrangementRequest(root, instruments = listOf("piano", "bass", "drums", "pad")))
        val generated = service.generateRequiredMidi(root)
        val renderer = CoreDraftRenderer()
        val coreMidi = mapOf(
            LogicalInstrument.PIANO to root.resolve("midi/clean/A.mid"),
            LogicalInstrument.BASS to root.resolve("midi/generated/bass.mid"),
            LogicalInstrument.DRUMS to root.resolve("midi/generated/drums.mid"),
            LogicalInstrument.PAD to root.resolve("midi/generated/pad.mid")
        )
        coreMidi.forEach { (instrument, midi) ->
            renderer.render(midi, instrument, root.resolve("draft/core/${instrument.wireName}.wav"), RenderFormat(), 100)
        }

        assertTrue(generated.artifacts.map { it.instrument }.containsAll(listOf("bass", "drums", "pad")))
        assertTrue(coreMidi.keys.all { Files.isRegularFile(root.resolve("draft/core/${it.wireName}.wav")) })
        assertFalse(Files.exists(root.resolve("draft/core/strings.wav")))
    }

    private fun project(name: String, structure: List<String> = listOf("A")): Path {
        val root = tempDir.resolve(name)
        Files.createDirectories(root.resolve("source")); Files.createDirectories(root.resolve("midi/clean"))
        writeMidi(root.resolve("source/A.mid")); writeMidi(root.resolve("midi/clean/A.mid"))
        val project = Project(
            name = name,
            parts = listOf(Part("A", "source/A.mid", "verse", midi = canonicalMidiReferences(root, "A"))),
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
        )
        ProjectStore.write(root, project)
        MidiAnalysisStore.write(root, project, "A", MidiPartAnalyzer().analyze(root.resolve("midi/clean/A.mid"), "A"))
        return root
    }

    private fun writeMidi(path: Path) {
        val sequence = Sequence(Sequence.PPQ, 480); val track = sequence.createTrack()
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, 60, 90), 0))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, 60, 0), 1_920))
        MidiSystem.write(sequence, 1, path.toFile())
    }

    private fun sha256(path: Path): String = java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }

    private fun coreLibrary(root: Path): Path {
        val library = root.resolve("core-draft-library")
        val source = TestSoundLibrary.root()
        Files.walk(source).use { paths -> paths.forEach { path ->
            val target = library.resolve(source.relativize(path).toString())
            if (Files.isDirectory(path)) Files.createDirectories(target) else Files.copy(path, target)
        } }
        mapOf("piano" to "C2", "bass" to "E1", "pad" to "C2", "strings" to "C2").forEach { (name, sample) ->
            Files.writeString(library.resolve("$name/$name.sfz"), "<region> sample=samples/$sample.wav lokey=0 hikey=127")
        }
        return library
    }

    private class CoreDraftRenderer : InstrumentRenderer {
        override suspend fun render(midi: Path, instrument: LogicalInstrument, output: Path, format: RenderFormat, expectedFrames: Long): RenderResult {
            Files.createDirectories(requireNotNull(output.parent))
            val audio = AudioBuffer(
                FloatArray((expectedFrames * format.channels).toInt()) { 0.1f },
                AudioFormat(format.sampleRate, format.channels, 24, false, false, "WAV"), expectedFrames.toDouble() / format.sampleRate
            )
            DeterministicStemMixer().writeWav(MixedStem(audio, listOf(instrument.wireName)), output)
            return RenderResult(output, format.sampleRate, format.channels, 24, expectedFrames, audio.duration, 0.1, "fake", "core", "", "")
        }
    }
}
