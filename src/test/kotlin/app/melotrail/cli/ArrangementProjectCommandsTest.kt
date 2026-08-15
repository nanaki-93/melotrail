package app.melotrail.cli

import app.melotrail.arrangement.Arrangement
import app.melotrail.arrangement.ArrangementSection
import app.melotrail.arrangement.ArrangementStore
import app.melotrail.arrangement.DetailedArrangement
import app.melotrail.arrangement.InstrumentPlan
import app.melotrail.arrangement.InstrumentMode
import app.melotrail.arrangement.MidiReferences
import app.melotrail.arrangement.MidiAnalysis
import app.melotrail.arrangement.MidiAnalysisStore
import app.melotrail.arrangement.MidiTempoChange
import app.melotrail.arrangement.MidiTimeSignature
import app.melotrail.arrangement.PartAnalysis
import app.melotrail.arrangement.PartAnalysisStore
import app.melotrail.arrangement.Part
import app.melotrail.arrangement.Project
import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.RenderFormat
import app.melotrail.arrangement.SongPlan
import app.melotrail.arrangement.SectionVariationPlan
import app.melotrail.audio.AudioBuffer
import app.melotrail.audio.AudioFormat
import kotlinx.serialization.decodeFromString
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
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage

class ArrangementProjectCommandsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `project create initializes v2 project json and MIDI-first directories`() {
        val projectRoot = tempDir.resolve("demo")

        val result = ArrangementProjectCommands.execute(
            arrayOf("project", "create", projectRoot.toString())
        )

        val project = readProject(projectRoot)
        assertTrue(result.contains("Created project"))
        assertTrue(Files.isDirectory(projectRoot.resolve("source")))
        assertTrue(Files.isDirectory(projectRoot.resolve("midi/clean")))
        assertEquals(Project.CURRENT_VERSION, project.version)
        assertEquals("demo", project.name)
        assertTrue(project.parts.isEmpty())
    }

    @Test
    fun `part add copies supported audio preserves source and updates project json`() {
        val projectRoot = createProject("demo")
        val source = tempDir.resolve("piano.wav")
        Files.writeString(source, "original source bytes")
        val sourceBefore = Files.readString(source)

        val result = ArrangementProjectCommands.execute(
            arrayOf(
                "part", "add", projectRoot.toString(),
                "--id", "A", "--file", source.toString(), "--role", "verse"
            )
        )

        val copied = projectRoot.resolve("parts/A.wav")
        val project = readProject(projectRoot)
        assertTrue(result.contains("Added legacy audio part 'A'"))
        assertEquals(sourceBefore, Files.readString(source))
        assertEquals(sourceBefore, Files.readString(copied))
        assertEquals(listOf("A"), project.parts.map { it.id })
        assertEquals("parts/A.wav", project.parts.single().file)
        assertEquals("verse", project.parts.single().role)
    }

    @Test
    fun `part add rejects duplicate ids without overwriting the imported file`() {
        val projectRoot = createProject("demo")
        val firstSource = tempDir.resolve("first.wav")
        val secondSource = tempDir.resolve("second.wav")
        Files.writeString(firstSource, "first source")
        Files.writeString(secondSource, "second source")
        addPart(projectRoot, "A", firstSource)
        val copiedBefore = Files.readString(projectRoot.resolve("parts/A.wav"))

        val exception = assertThrows(IllegalArgumentException::class.java) {
            addPart(projectRoot, "A", secondSource)
        }

        assertTrue(exception.message.orEmpty().contains("Part ID already exists: A"))
        assertEquals(copiedBefore, Files.readString(projectRoot.resolve("parts/A.wav")))
        assertEquals(1, readProject(projectRoot).parts.size)
    }

    @Test
    fun `part add rejects unsupported input without modifying project`() {
        val projectRoot = createProject("demo")
        val unsupported = tempDir.resolve("notes.txt")
        Files.writeString(unsupported, "not audio")

        val exception = assertThrows(IllegalArgumentException::class.java) {
            addPart(projectRoot, "notes", unsupported)
        }

        assertTrue(exception.message.orEmpty().contains("Unsupported input file extension"))
        assertFalse(Files.exists(projectRoot.resolve("parts/notes.txt")))
        assertTrue(readProject(projectRoot).parts.isEmpty())
    }

    @Test
    fun `arrange explicitly selects deterministic global planner and writes song plan json`() {
        val projectRoot = createMidiPlanningProject("demo")

        val result = ArrangementProjectCommands.execute(
            arrayOf(
                "arrange", "--project", projectRoot.toString(), "--planner", "deterministic",
                "--structure", "A A", "--instruments", "piano,bass", "--style", "warm"
            )
        )

        val songPlan = json.decodeFromString<SongPlan>(
            Files.readString(projectRoot.resolve("song_plan.json"))
        )
        val variations = json.decodeFromString<SectionVariationPlan>(
            Files.readString(projectRoot.resolve("section_variations.json"))
        )
        assertTrue(ArrangementProjectCommands.handles(arrayOf("arrange")))
        assertTrue(result.contains("Created deterministic global song plan"))
        assertEquals(listOf("A", "A"), songPlan.sections.map { it.partId })
        assertEquals(listOf("A1", "A2"), songPlan.sections.map { it.instanceId })
        assertEquals(listOf(1, 2), songPlan.sections.map { it.occurrence })
        assertEquals(listOf("piano"), songPlan.sections.first().instrumentProgression)
        assertEquals(listOf("A1", "A2"), variations.sections.map { it.instanceId })
        assertFalse(Files.exists(projectRoot.resolve("arrangement.json")))
    }

    @Test
    fun `arrange detail expands reviewed artifacts into an approved v3 arrangement`() {
        val projectRoot = createMidiPlanningProject("detailed-demo")
        ArrangementProjectCommands.execute(
            arrayOf("arrange", "--project", projectRoot.toString(), "--planner", "deterministic", "--instruments", "piano,bass")
        )

        val result = ArrangementProjectCommands.execute(
            arrayOf("arrange-detail", "--project", projectRoot.toString(), "--planner", "deterministic")
        )

        val arrangement = json.decodeFromString<DetailedArrangement>(Files.readString(projectRoot.resolve("arrangement.json")))
        assertTrue(ArrangementProjectCommands.handles(arrayOf("arrange-detail")))
        assertTrue(result.contains("Created deterministic detailed arrangement"))
        assertEquals(3, arrangement.version)
        assertEquals("A1", arrangement.sections.single().instanceId)
    }

    @Test
    fun `preview and approve validate a v3 draft without attempting audio rendering`() {
        val projectRoot = createMidiPlanningProject("detailed-draft-demo")
        ArrangementProjectCommands.execute(arrayOf("arrange", "--project", projectRoot.toString(), "--planner", "deterministic"))
        ArrangementProjectCommands.execute(arrayOf("arrange-detail", "--project", projectRoot.toString(), "--planner", "deterministic"))
        Files.copy(projectRoot.resolve("arrangement.json"), projectRoot.resolve("arrangement.draft.json"), java.nio.file.StandardCopyOption.REPLACE_EXISTING)

        val preview = ArrangementProjectCommands.execute(arrayOf("preview", "--project", projectRoot.toString()))
        val approve = ArrangementProjectCommands.execute(arrayOf("approve", "--project", projectRoot.toString()))

        assertTrue(preview.contains("Validated detailed arrangement draft"))
        assertTrue(Files.readString(projectRoot.resolve("previews/detailed-arrangement-preview.txt")).contains("Detailed arrangement draft review"))
        assertTrue(approve.contains("Approved detailed arrangement"))
        assertEquals(3, json.decodeFromString<DetailedArrangement>(Files.readString(projectRoot.resolve("arrangement.json"))).version)
    }

    @Test
    fun `deterministic critic snapshots approved v3 arrangement and requires explicit approval`() {
        val projectRoot = createMidiPlanningProject("critic-demo")
        ArrangementProjectCommands.execute(arrayOf("arrange", "--project", projectRoot.toString(), "--planner", "deterministic"))
        ArrangementProjectCommands.execute(arrayOf("arrange-detail", "--project", projectRoot.toString(), "--planner", "deterministic"))
        val approved = projectRoot.resolve("arrangement.json")
        val approvedBytes = Files.readAllBytes(approved)

        val result = ArrangementProjectCommands.execute(arrayOf("critic", "--project", projectRoot.toString(), "--planner", "deterministic"))

        assertTrue(ArrangementProjectCommands.handles(arrayOf("critic")))
        assertTrue(result.contains("arrangement-critic draft"))
        assertEquals(approvedBytes.toList(), Files.readAllBytes(approved).toList())
        assertEquals(approvedBytes.toList(), Files.readAllBytes(projectRoot.resolve("arrangement_v1.json")).toList())
        assertEquals(
            json.decodeFromString<DetailedArrangement>(Files.readString(approved)),
            json.decodeFromString<DetailedArrangement>(Files.readString(projectRoot.resolve("arrangement.draft.json")))
        )
    }

    @Test
    fun `arrange rejects unknown planners without writing a song plan`() {
        val projectRoot = createMidiPlanningProject("demo")

        val exception = assertThrows(IllegalArgumentException::class.java) {
            ArrangementProjectCommands.execute(
                arrayOf("arrange", "--project", projectRoot.toString(), "--planner", "unknown")
            )
        }

        assertEquals("Unsupported planner: unknown. Available planners: deterministic, qwen", exception.message)
        assertFalse(Files.exists(projectRoot.resolve("song_plan.json")))
    }

    @Test
    fun `generate bass writes inspectable MIDI without changing source MIDI`() {
        val projectRoot = tempDir.resolve("midi-demo")
        val clean = projectRoot.resolve("midi/clean/A.mid")
        Files.createDirectories(clean.parent)
        val sequence = Sequence(Sequence.PPQ, 480)
        val track = sequence.createTrack()
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, 60, 100), 0))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, 64, 90), 0))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, 67, 80), 0))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, 60, 0), 1920))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, 64, 0), 1920))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, 67, 0), 1920))
        MidiSystem.write(sequence, 1, clean.toFile())
        val source = projectRoot.resolve("source/A.mid")
        Files.createDirectories(source.parent)
        Files.copy(clean, source)
        val sourceBefore = Files.readAllBytes(source)
        val project = Project(Project.CURRENT_VERSION, "midi-demo", listOf(Part("A", "source/A.mid", midi = MidiReferences(clean = "midi/clean/A.mid"))), renderFormat = RenderFormat())
        ProjectStore.write(projectRoot, project)
        ArrangementProjectCommands.execute(arrayOf("part", "analyze", projectRoot.toString(), "--id", "A"))
        ArrangementStore.write(
            projectRoot,
            readProject(projectRoot),
            Arrangement(
                sections = listOf(
                    ArrangementSection(
                        index = 0,
                        partId = "A",
                        instruments = listOf(
                            InstrumentPlan("piano", InstrumentMode.SOURCE),
                            InstrumentPlan("bass", InstrumentMode.GENERATED, "root_fifth", 0.5)
                        )
                    )
                )
            )
        )

        val result = ArrangementProjectCommands.execute(
            arrayOf("generate", "bass", "--project", projectRoot.toString())
        )

        assertTrue(ArrangementProjectCommands.handles(arrayOf("generate")))
        assertTrue(result.contains("Generated bass MIDI"))
        assertTrue(Files.isRegularFile(projectRoot.resolve("midi/generated/bass.mid")))
        assertEquals(sourceBefore.toList(), Files.readAllBytes(source).toList())
    }

    @Test
    fun `generate drums writes v3 registry-mapped MIDI without changing source MIDI`() {
        val projectRoot = createMidiPlanningProject("drum-demo")
        val source = projectRoot.resolve("source/A.mid")
        val sourceBefore = Files.readAllBytes(source)
        ArrangementProjectCommands.execute(
            arrayOf("arrange", "--project", projectRoot.toString(), "--planner", "deterministic", "--structure", "A A A", "--instruments", "piano,drums")
        )
        ArrangementProjectCommands.execute(
            arrayOf("arrange-detail", "--project", projectRoot.toString(), "--planner", "deterministic")
        )

        val result = ArrangementProjectCommands.execute(
            arrayOf("generate", "drums", "--project", projectRoot.toString())
        )

        assertTrue(result.contains("Generated drum MIDI"))
        assertTrue(Files.isRegularFile(projectRoot.resolve("midi/generated/drums.mid")))
        assertEquals(sourceBefore.toList(), Files.readAllBytes(source).toList())
    }

    @Test
    fun `generate pad writes v3 sustained harmony MIDI without changing source MIDI`() {
        val projectRoot = createMidiPlanningProject("pad-demo")
        val source = projectRoot.resolve("source/A.mid")
        val sourceBefore = Files.readAllBytes(source)
        ArrangementProjectCommands.execute(
            arrayOf("arrange", "--project", projectRoot.toString(), "--planner", "deterministic", "--structure", "A A A", "--instruments", "piano,pad")
        )
        ArrangementProjectCommands.execute(
            arrayOf("arrange-detail", "--project", projectRoot.toString(), "--planner", "deterministic")
        )

        val result = ArrangementProjectCommands.execute(
            arrayOf("generate", "pad", "--project", projectRoot.toString())
        )

        assertTrue(result.contains("Generated pad MIDI"))
        assertTrue(Files.isRegularFile(projectRoot.resolve("midi/generated/pad.mid")))
        assertEquals(sourceBefore.toList(), Files.readAllBytes(source).toList())
    }

    @Test
    fun `generate strings writes v3 registry-mapped MIDI without changing earlier generated MIDI`() {
        val projectRoot = createMidiPlanningProject("strings-demo")
        val source = projectRoot.resolve("source/A.mid")
        val sourceBefore = Files.readAllBytes(source)
        val pad = projectRoot.resolve("midi/generated/pad.mid")
        Files.createDirectories(pad.parent)
        Files.writeString(pad, "pad MIDI remains untouched")
        val padBefore = Files.readAllBytes(pad)
        ArrangementProjectCommands.execute(
            arrayOf("arrange", "--project", projectRoot.toString(), "--planner", "deterministic", "--structure", "A A A", "--instruments", "piano,strings")
        )
        ArrangementProjectCommands.execute(
            arrayOf("arrange-detail", "--project", projectRoot.toString(), "--planner", "deterministic")
        )

        val result = ArrangementProjectCommands.execute(
            arrayOf("generate", "strings", "--project", projectRoot.toString())
        )

        assertTrue(result.contains("Generated strings MIDI"))
        assertTrue(Files.isRegularFile(projectRoot.resolve("midi/generated/strings.mid")))
        assertEquals(sourceBefore.toList(), Files.readAllBytes(source).toList())
        assertTrue(Files.readAllBytes(pad).contentEquals(padBefore))
    }

    @Test
    fun `generate transitions writes an inspectable deterministic midi artifact`() {
        val projectRoot = createMidiPlanningProject("transition-demo")
        val source = projectRoot.resolve("source/A.mid")
        val sourceBefore = Files.readAllBytes(source)
        ArrangementProjectCommands.execute(
            arrayOf("arrange", "--project", projectRoot.toString(), "--planner", "deterministic", "--structure", "A A A", "--instruments", "piano,bass,drums,pad")
        )
        ArrangementProjectCommands.execute(
            arrayOf("arrange-detail", "--project", projectRoot.toString(), "--planner", "deterministic")
        )

        val result = ArrangementProjectCommands.execute(
            arrayOf("generate", "transitions", "--project", projectRoot.toString())
        )

        assertTrue(result.contains("Generated transition MIDI"))
        assertTrue(Files.isRegularFile(projectRoot.resolve("midi/generated/transitions.mid")))
        assertEquals(sourceBefore.toList(), Files.readAllBytes(source).toList())
    }

    @Test
    fun `mix creates full and dry lossless WAV files without a fixed-tone bass`() {
        val projectRoot = createProject("mix-demo")
        val source = tempDir.resolve("piano.wav")
        writeSourceWav(source, 32_000, 1, 320)
        addPart(projectRoot, "A", source)
        val copiedSource = projectRoot.resolve("parts/A.wav")
        val sourceBefore = Files.readAllBytes(copiedSource)
        val project = readProject(projectRoot)
        PartAnalysisStore.write(
            projectRoot,
            project,
            "A",
            PartAnalysis(0.01, 32_000, 1, 320, 0.5, 0.25, false)
        )
        val updatedProject = readProject(projectRoot)
        ArrangementStore.write(
            projectRoot,
            updatedProject,
            Arrangement(
                sections = listOf(
                    ArrangementSection(
                        index = 0,
                        partId = "A",
                        instruments = listOf(
                            InstrumentPlan("piano", InstrumentMode.SOURCE),
                            InstrumentPlan("drums", InstrumentMode.GENERATED, "supporting", 0.5)
                        )
                    )
                )
            )
        )
        val fullResult = ArrangementProjectCommands.execute(arrayOf("mix", "--project", projectRoot.toString()))
        val mixPath = projectRoot.resolve("mix/mix.wav")
        val fullMix = Files.readAllBytes(mixPath)
        val dryResult = ArrangementProjectCommands.execute(
            arrayOf("mix", "--project", projectRoot.toString(), "--dry")
        )
        val dryMix = Files.readAllBytes(mixPath)

        assertTrue(ArrangementProjectCommands.handles(arrayOf("mix")))
        assertTrue(fullResult.contains("Created mix"))
        assertTrue(dryResult.contains("Created dry mix"))
        assertEquals("RIFF", fullMix.copyOfRange(0, 4).decodeToString())
        assertEquals("RIFF", dryMix.copyOfRange(0, 4).decodeToString())
        assertFalse(fullMix.contentEquals(dryMix))
        assertTrue(Files.readAllBytes(copiedSource).contentEquals(sourceBefore))
    }

    @Test
    fun `mix publishes an already rendered detailed arrangement dry mix`() {
        val projectRoot = createMidiPlanningProject("v3-mix-demo")
        ArrangementProjectCommands.execute(arrayOf(
            "arrange", "--project", projectRoot.toString(), "--planner", "deterministic",
            "--structure", "A A A", "--instruments", "piano,bass"
        ))
        ArrangementProjectCommands.execute(arrayOf(
            "arrange-detail", "--project", projectRoot.toString(), "--planner", "deterministic"
        ))
        val dry = projectRoot.resolve("mix/dry.wav")
        Files.createDirectories(dry.parent)
        writeSourceWav(dry, 44_100, 2, 441)
        val expected = Files.readAllBytes(dry)

        val result = ArrangementProjectCommands.execute(arrayOf("mix", "--project", projectRoot.toString()))

        assertTrue(result.contains("rendered v3 stems"))
        assertTrue(Files.readAllBytes(projectRoot.resolve("mix/mix.wav")).contentEquals(expected))
    }

    @Test
    fun `build masters the repaired dry mix with preserved PCM-24 format and release metadata`() {
        val projectRoot = createProject("build-demo")
        val source = tempDir.resolve("piano.wav")
        writeSourceWav(source, 32_000, 1, 320)
        addPart(projectRoot, "A", source)
        writeProject(readProject(projectRoot).copy(structure = listOf("A")), projectRoot)
        val copiedSource = projectRoot.resolve("parts/A.wav")
        val sourceBefore = Files.readAllBytes(copiedSource)
        val output = projectRoot.resolve("rendered")

        val worker = RecordingBuildWorker()
        val result = ArrangementProjectCommands.executeBuildForTest(
            arrayOf(
                "build", "--project", projectRoot.toString(),
                "--output-dir", "rendered", "--no-ai"
            ),
            worker
        )

        assertTrue(result.contains("[1/10] Loaded project"))
        assertTrue(result.contains("[10/10] Build complete"))
        assertTrue(Files.isRegularFile(projectRoot.resolve("analysis/A.json")))
        assertTrue(Files.isRegularFile(projectRoot.resolve("arrangement.json")))
        assertTrue(Files.isRegularFile(projectRoot.resolve("stems/drums.wav")))
        assertWav(projectRoot.resolve("mix/mix.wav"), sampleRate = 32_000, channels = 1)
        assertWav(projectRoot.resolve("mix/dry.wav"), sampleRate = 32_000, channels = 1, bitDepth = 24)
        assertWav(projectRoot.resolve("mix/repaired.wav"), sampleRate = 32_000, channels = 1, bitDepth = 24)
        assertFalse(Files.exists(projectRoot.resolve("mix/lofi.wav")))
        assertWav(output.resolve("master.wav"), sampleRate = 32_000, channels = 1, bitDepth = 24)
        assertEquals(Files.size(projectRoot.resolve("mix/dry.wav")), Files.size(projectRoot.resolve("mix/repaired.wav")))
        assertEquals(Files.size(projectRoot.resolve("mix/repaired.wav")), Files.size(output.resolve("master.wav")))
        assertFalse(output.resolve("master.wav").fileName.toString().endsWith(".mp3"))
        assertTrue(result.contains("mastering input: mix/repaired.wav"))
        assertEquals(listOf("health", "analyze", "repair:dry.wav", "master:repaired.wav"), worker.events)
        val release = Files.readString(output.resolve("release.json"))
        assertTrue(release.contains("\"inputArtifact\": \"mix/repaired.wav\""))
        assertTrue(release.contains("\"pcmBitDepth\": 24"))
        assertTrue(release.contains("\"targetLufs\": -14.0"))
        assertTrue(Files.readAllBytes(copiedSource).contentEquals(sourceBefore))
    }

    @Test
    fun `build uses mix lofi only when explicitly enabled`() {
        val projectRoot = createProject("lofi-build-demo")
        val source = tempDir.resolve("piano.wav")
        writeSourceWav(source, 44_100, 2, 320)
        addPart(projectRoot, "A", source)
        writeProject(readProject(projectRoot).copy(structure = listOf("A")), projectRoot)
        val worker = RecordingBuildWorker()

        val result = ArrangementProjectCommands.executeBuildForTest(
            arrayOf("build", "--project", projectRoot.toString(), "--lofi"), worker
        )

        assertTrue(result.contains("mastering input: mix/lofi.wav"))
        assertWav(projectRoot.resolve("mix/lofi.wav"), sampleRate = 44_100, channels = 2, bitDepth = 24)
        assertEquals("master:lofi.wav", worker.events.last())
        assertTrue(Files.readString(projectRoot.resolve("output/release.json")).contains("\"loFiEnabled\": true"))
    }

    @Test
    fun `build optionally exports song mp3 and records release metadata`() {
        val projectRoot = createBuildProject("mp3-build-demo")
        val worker = RecordingBuildWorker(mp3Writer = { output -> writeFakeMp3(output) })

        val result = ArrangementProjectCommands.executeBuildForTest(
            arrayOf("build", "--project", projectRoot.toString(), "--mp3", "--mp3-bitrate", "256"), worker
        )

        val song = projectRoot.resolve("output/song.mp3")
        assertTrue(Files.isRegularFile(song))
        assertEquals("ID3", Files.readAllBytes(song).copyOfRange(0, 3).decodeToString())
        assertTrue(result.contains("song.mp3"))
        val release = Files.readString(projectRoot.resolve("output/release.json"))
        assertTrue(release.contains("\"name\": \"song.mp3\""))
        assertTrue(release.contains("\"bitrateKbps\": 256"))
        assertTrue(release.contains("\"instrumentLicenses\""))
        assertEquals("mp3:master.wav:256", worker.events.last())
    }

    @Test
    fun `build retains master when optional MP3 encoder is unavailable`() {
        val projectRoot = createBuildProject("mp3-unavailable-demo")

        val result = ArrangementProjectCommands.executeBuildForTest(
            arrayOf("build", "--project", projectRoot.toString(), "--mp3"), RecordingBuildWorker()
        )

        assertTrue(Files.isRegularFile(projectRoot.resolve("output/master.wav")))
        assertFalse(Files.exists(projectRoot.resolve("output/song.mp3")))
        assertTrue(result.contains("MP3 export unavailable"))
    }

    @Test
    fun `export mp3 accepts only final master and validates output`() {
        val master = tempDir.resolve("output/master.wav")
        Files.createDirectories(master.parent)
        writeSourceWav(master, 22_050, 1, 64)
        val output = master.parent.resolve("song.mp3")
        val worker = RecordingBuildWorker(mp3Writer = { path -> writeFakeMp3(path) })

        val result = ArrangementProjectCommands.executeExportMp3ForTest(
            arrayOf("export-mp3", "--input", master.toString(), "--output", output.toString(), "--bitrate", "128"), worker
        )

        assertTrue(result.contains("128 kbps"))
        assertTrue(Files.isRegularFile(output))
        assertEquals("mp3:master.wav:128", worker.events.single())
        assertThrows(IllegalArgumentException::class.java) {
            ArrangementProjectCommands.executeExportMp3ForTest(
                arrayOf("export-mp3", "--input", master.toString(), "--output", master.parent.resolve("song.wav").toString()), worker
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ArrangementProjectCommands.executeExportMp3ForTest(
                arrayOf("export-mp3", "--input", master.toString(), "--output", output.toString(), "--bitrate", "111"), worker
            )
        }
    }

    @Test
    fun `explicit MP3 export reports missing encoder as required failure`() {
        val master = tempDir.resolve("output/master.wav")
        Files.createDirectories(master.parent)
        writeSourceWav(master, 22_050, 1, 64)

        val error = assertThrows(IllegalStateException::class.java) {
            ArrangementProjectCommands.executeExportMp3ForTest(
                arrayOf("export-mp3", "--input", master.toString(), "--output", master.parent.resolve("song.mp3").toString()),
                RecordingBuildWorker()
            )
        }
        assertTrue(error.message.orEmpty().contains("requires the optional local lameenc encoder"))
    }

    @Test
    fun `build keeps an existing master when temporary worker output is malformed`() {
        val projectRoot = createBuildProject("atomic-master-demo")
        val master = projectRoot.resolve("output/master.wav")
        Files.createDirectories(master.parent)
        writeSourceWav(master, 32_000, 1, 64)
        val before = Files.readAllBytes(master)

        val exception = assertThrows(IllegalStateException::class.java) {
            ArrangementProjectCommands.executeBuildForTest(
                arrayOf("build", "--project", projectRoot.toString()),
                RecordingBuildWorker(masterWriter = { _, output -> Files.writeString(output, "not a wav") })
            )
        }

        assertTrue(exception.message.orEmpty().contains("Master audio failed"))
        assertEquals(before.toList(), Files.readAllBytes(master).toList())
        assertFalse(Files.list(master.parent).use { paths -> paths.anyMatch { it.fileName.toString().contains(".mastering-") } })
    }

    @Test
    fun `build rejects non finite or over ceiling master output`() {
        val nonFinite = createBuildProject("non-finite-master-demo")
        val nonFiniteFailure = assertThrows(IllegalStateException::class.java) {
            ArrangementProjectCommands.executeBuildForTest(
                arrayOf("build", "--project", nonFinite.toString()),
                RecordingBuildWorker(masterWriter = { _, output -> writeFloatNanWav(output) })
            )
        }
        assertTrue(nonFiniteFailure.message.orEmpty().contains("non-finite"))

        val clipped = createBuildProject("clipped-master-demo")
        val clippedFailure = assertThrows(IllegalStateException::class.java) {
            ArrangementProjectCommands.executeBuildForTest(
                arrayOf("build", "--project", clipped.toString()),
                RecordingBuildWorker(masterWriter = { _, output -> writeSourceWav(output, 32_000, 1, 320, 0.95f) })
            )
        }
        assertTrue(clippedFailure.message.orEmpty().contains("peak ceiling"))
    }

    @Test
    fun `build reports worker health repair and mastering failures by stage`() {
        val health = createBuildProject("health-failure-demo")
        val healthFailure = assertThrows(IllegalArgumentException::class.java) {
            ArrangementProjectCommands.executeBuildForTest(arrayOf("build", "--project", health.toString()), RecordingBuildWorker(failure = "health"))
        }
        assertTrue(healthFailure.message.orEmpty().contains("worker health"))

        val repair = createBuildProject("repair-failure-demo")
        val repairFailure = assertThrows(IllegalStateException::class.java) {
            ArrangementProjectCommands.executeBuildForTest(arrayOf("build", "--project", repair.toString()), RecordingBuildWorker(failure = "repair"))
        }
        assertTrue(repairFailure.message.orEmpty().contains("Repair mix failed"))

        val master = createBuildProject("master-failure-demo")
        val masterFailure = assertThrows(IllegalStateException::class.java) {
            ArrangementProjectCommands.executeBuildForTest(arrayOf("build", "--project", master.toString()), RecordingBuildWorker(failure = "master"))
        }
        assertTrue(masterFailure.message.orEmpty().contains("Master audio failed"))
    }

    @Test
    fun `build dry run validates project but does not write derived files or require worker`() {
        val projectRoot = createProject("dry-run-demo")
        val source = tempDir.resolve("piano.wav")
        writeSourceWav(source, 22_050, 1, 64)
        addPart(projectRoot, "A", source)
        writeProject(readProject(projectRoot).copy(structure = listOf("A")), projectRoot)
        val copiedSource = projectRoot.resolve("parts/A.wav")
        val sourceBefore = Files.readAllBytes(copiedSource)

        val result = ArrangementProjectCommands.executeBuildForTest(
            arrayOf("build", "--project", projectRoot.toString(), "--dry-run"),
            object : ArrangementProjectCommands.BuildWorker {
                override suspend fun healthCheck(): Boolean = error("worker must not be used for dry run")
                override suspend fun analyze(path: Path): PartAnalysis = error("worker must not be used for dry run")
                override suspend fun repair(inputPath: Path, outputPath: Path) = error("worker must not be used for dry run")
                override suspend fun master(inputPath: Path, outputPath: Path) = error("worker must not be used for dry run")
            }
        )

        assertTrue(result.contains("[DRY RUN] Project is valid"))
        assertFalse(Files.exists(projectRoot.resolve("analysis/A.json")))
        assertFalse(Files.exists(projectRoot.resolve("arrangement.json")))
        assertFalse(Files.exists(projectRoot.resolve("stems/bass.wav")))
        assertFalse(Files.exists(projectRoot.resolve("output/master.wav")))
        assertTrue(Files.readAllBytes(copiedSource).contentEquals(sourceBefore))
    }

    @Test
    fun `build rejects an output directory that would overwrite a source part`() {
        val projectRoot = createProject("protected-source-demo")
        val source = tempDir.resolve("master.wav")
        writeSourceWav(source, 22_050, 1, 64)
        addPart(projectRoot, "master", source)
        writeProject(readProject(projectRoot).copy(structure = listOf("master")), projectRoot)
        val copiedSource = projectRoot.resolve("parts/master.wav")
        val sourceBefore = Files.readAllBytes(copiedSource)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            ArrangementProjectCommands.executeBuildForTest(
                arrayOf("build", "--project", projectRoot.toString(), "--output-dir", "parts", "--dry-run"),
                RecordingBuildWorker()
            )
        }

        assertTrue(exception.message.orEmpty().contains("would overwrite a source audio file"))
        assertTrue(Files.readAllBytes(copiedSource).contentEquals(sourceBefore))
    }

    private fun createProject(name: String): Path {
        val projectRoot = tempDir.resolve(name)
        ArrangementProjectCommands.execute(arrayOf("project", "create", projectRoot.toString()))
        // Existing arrangement tests exercise legacy source-audio behavior.
        Files.createDirectories(projectRoot.resolve("parts"))
        ProjectStore.write(projectRoot, Project(name = name))
        return projectRoot
    }

    private fun createMidiPlanningProject(name: String): Path {
        val projectRoot = tempDir.resolve(name)
        val source = projectRoot.resolve("source/A.mid")
        val clean = projectRoot.resolve("midi/clean/A.mid")
        Files.createDirectories(source.parent)
        Files.createDirectories(clean.parent)
        Files.write(source, byteArrayOf(0x4d, 0x54, 0x68, 0x64))
        Files.write(clean, byteArrayOf(0x4d, 0x54, 0x68, 0x64))
        val project = Project(
            version = Project.CURRENT_VERSION,
            name = name,
            parts = listOf(Part("A", "source/A.mid", "verse", midi = MidiReferences(clean = "midi/clean/A.mid"))),
            structure = listOf("A"),
            renderFormat = RenderFormat()
        )
        ProjectStore.write(projectRoot, project)
        MidiAnalysisStore.write(projectRoot, project, "A", MidiAnalysis(
            partId = "A",
            ppq = 480,
            durationTicks = 1_920,
            durationSeconds = 2.0,
            tempoMap = listOf(MidiTempoChange(0, 120.0)),
            timeSignatures = listOf(MidiTimeSignature(0, 4, 4)),
            bars = 1,
            beats = 4.0,
            noteCount = 4,
            noteDensity = 0.25,
            rhythmicDensity = 0.5,
            energy = 0.5
        ))
        return projectRoot
    }

    private fun addPart(projectRoot: Path, id: String, source: Path) {
        ArrangementProjectCommands.execute(
            arrayOf("part", "add", projectRoot.toString(), "--id", id, "--file", source.toString())
        )
    }

    private fun readProject(projectRoot: Path): Project = ProjectStore.read(projectRoot)

    private fun writeProject(project: Project, projectRoot: Path) {
        ProjectStore.write(projectRoot, project)
    }

    private fun createBuildProject(name: String): Path {
        val projectRoot = createProject(name)
        val source = tempDir.resolve("$name.wav")
        writeSourceWav(source, 32_000, 1, 320)
        addPart(projectRoot, "A", source)
        writeProject(readProject(projectRoot).copy(structure = listOf("A")), projectRoot)
        return projectRoot
    }

    private fun writeSourceWav(path: Path, sampleRate: Int, channels: Int, frames: Int, sample: Float = 0.2f) {
        val format = AudioFormat(sampleRate, channels, 24, false, false, "WAV")
        WAVExporterSimple().export(AudioBuffer(FloatArray(frames * channels) { sample }, format, frames.toDouble() / sampleRate), path)
    }

    private fun writeFloatNanWav(path: Path) {
        val bytes = java.nio.ByteBuffer.allocate(48).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".encodeToByteArray()).putInt(40).put("WAVE".encodeToByteArray())
        bytes.put("fmt ".encodeToByteArray()).putInt(16).putShort(3).putShort(1).putInt(32_000).putInt(128_000).putShort(4).putShort(32)
        bytes.put("data".encodeToByteArray()).putInt(4).putInt(Float.NaN.toRawBits())
        Files.write(path, bytes.array())
    }

    private fun writeFakeMp3(path: Path) {
        Files.createDirectories(path.parent)
        Files.write(path, byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 4, 0, 0, 0, 0, 0, 0, 0xff.toByte(), 0xfb.toByte(), 0, 0))
    }

    private fun assertWav(path: Path, sampleRate: Int? = null, channels: Int? = null, bitDepth: Int? = null) {
        assertTrue(Files.size(path) >= 44)
        val bytes = Files.readAllBytes(path)
        assertEquals("RIFF", bytes.copyOfRange(0, 4).decodeToString())
        if (sampleRate != null || channels != null || bitDepth != null) {
            val header = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            sampleRate?.let { assertEquals(it, header.getInt(24)) }
            channels?.let { assertEquals(it, header.getShort(22).toInt()) }
            bitDepth?.let { assertEquals(it, header.getShort(34).toInt()) }
        }
    }

    private class RecordingBuildWorker(
        private val masterWriter: ((Path, Path) -> Unit)? = null,
        private val mp3Writer: ((Path) -> Unit)? = null,
        private val failure: String? = null
    ) : ArrangementProjectCommands.BuildWorker {
        val events = mutableListOf<String>()

        override suspend fun healthCheck(): Boolean {
            events += "health"
            return failure != "health"
        }

        override suspend fun analyze(path: Path): PartAnalysis {
            events += "analyze"
            return PartAnalysis(
                duration = 0.01,
                sampleRate = 32_000,
                channels = 1,
                frameCount = 320,
                peak = 0.5,
                rms = 0.25,
                nearSilence = false
            )
        }

        override suspend fun repair(inputPath: Path, outputPath: Path) {
            events += "repair:${inputPath.fileName}"
            if (failure == "repair") error("worker repair unavailable")
            Files.copy(inputPath, outputPath)
        }

        override suspend fun master(inputPath: Path, outputPath: Path) {
            events += "master:${inputPath.fileName}"
            if (failure == "master") error("worker master unavailable")
            masterWriter?.invoke(inputPath, outputPath) ?: Files.copy(inputPath, outputPath)
        }

        override suspend fun exportMp3(inputPath: Path, outputPath: Path, bitrateKbps: Int): ArrangementProjectCommands.Mp3ExportResult {
            events += "mp3:${inputPath.fileName}:$bitrateKbps"
            mp3Writer?.invoke(outputPath) ?: return ArrangementProjectCommands.Mp3ExportResult.Unavailable
            return ArrangementProjectCommands.Mp3ExportResult.Created("fake-lameenc")
        }
    }
}
