package app.melotrail.arrangement

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import javax.sound.midi.MidiSystem
import javax.sound.midi.ShortMessage
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class MidiTransitionEngineTest {
    private val engine = DeterministicMidiTransitionEngine()
    @TempDir lateinit var projectRoot: Path

    @Test
    fun `every transition type has bounded duration and cymbal remains unavailable`() {
        assertTrue(result(MidiTransitionType.NONE, 0).events.isEmpty())
        assertTrue(result(MidiTransitionType.DROP, 0).events.isEmpty())
        assertTrue(result(MidiTransitionType.DRUM_FILL, 1).events.any { it.instrument == LogicalInstrument.DRUMS })
        assertTrue(result(MidiTransitionType.BASS_WALK, 2).events.any { it.instrument == LogicalInstrument.BASS })
        assertTrue(result(MidiTransitionType.PAD_SUSTAIN, 1).events.any { it.instrument == LogicalInstrument.PAD })
        assertTrue(result(MidiTransitionType.BUILD, 1).events.map { it.instrument }.containsAll(setOf(LogicalInstrument.DRUMS, LogicalInstrument.BASS, LogicalInstrument.PAD)))

        assertThrows(IllegalArgumentException::class.java) { result(MidiTransitionType.DRUM_FILL, 0) }
        assertThrows(IllegalArgumentException::class.java) { result(MidiTransitionType.DROP, 1) }
        assertThrows(IllegalArgumentException::class.java) { result(MidiTransitionType.CYMBAL, 1) }
    }

    @Test
    fun `first middle and final boundaries account for inserted bars exactly once across meter changes`() {
        val sections = listOf(
            section(0, "A", duration = 1920),
            section(1, "B", duration = 1440, meter = MidiTimeSignature(0, 3, 4)),
            section(2, "C", duration = 1920)
        )
        val generated = engine.generate(
            sections,
            listOf(MidiTransitionPlan(MidiTransitionType.BASS_WALK, 1), MidiTransitionPlan(MidiTransitionType.DRUM_FILL, 2), MidiTransitionPlan()),
            instruments, drumMap
        )

        assertEquals(listOf(0L, 3360L, 8640L), generated.placements.map { it.startTick })
        assertEquals(listOf(1440L, 3840L, 0L), generated.placements.map { it.insertedTicksAfter })
        assertTrue(generated.events.all { it.startTick in 1920 until 3360 || it.startTick in 4800 until 8640 })
        assertThrows(IllegalArgumentException::class.java) {
            engine.generate(sections, listOf(MidiTransitionPlan(), MidiTransitionPlan(), MidiTransitionPlan(MidiTransitionType.BUILD, 1)), instruments, drumMap)
        }
    }

    @Test
    fun `bass walk uses only confident boundary harmony including Am to F and degrades conservatively`() {
        val walk = result(MidiTransitionType.BASS_WALK, 1).events.filter { it.instrument == LogicalInstrument.BASS }
        assertEquals(listOf(45, 44, 42, 41), walk.map { it.pitch }) // A2 -> G#2 -> F#2 -> F2

        val weak = engine.generate(
            listOf(section(0, "A", finalChord = chord("Am", confidence = 0.74)), section(1, "B")),
            listOf(MidiTransitionPlan(MidiTransitionType.BASS_WALK, 1), MidiTransitionPlan()), instruments, drumMap
        )
        assertTrue(weak.events.isEmpty())
        assertTrue(weak.diagnostics.single().contains("low-confidence"))
    }

    @Test
    fun `pad hold collision filtering unavailable instruments and regeneration stay deterministic`() {
        val first = result(MidiTransitionType.PAD_SUSTAIN, 1)
        assertTrue(first.events.all { it.instrument == LogicalInstrument.PAD && it.endTick <= 3840 })
        assertEquals(first, result(MidiTransitionType.PAD_SUSTAIN, 1))

        val collision = engine.generate(
            listOf(section(0, "A"), section(1, "B")), listOf(MidiTransitionPlan(MidiTransitionType.BASS_WALK, 1), MidiTransitionPlan()), instruments, drumMap,
            occupied = listOf(TransitionMidiEvent(LogicalInstrument.BASS, 1920, 2280, 45, 80))
        )
        assertTrue(collision.events.none { it.startTick == 1920L && it.pitch == 45 })
        assertTrue(collision.diagnostics.any { it.contains("Dropped colliding") })

        val noBass = engine.generate(
            listOf(section(0, "A", instruments = setOf(LogicalInstrument.PAD)), section(1, "B", instruments = setOf(LogicalInstrument.PAD))),
            listOf(MidiTransitionPlan(MidiTransitionType.BASS_WALK, 1), MidiTransitionPlan()), instruments, drumMap
        )
        assertTrue(noBass.events.isEmpty())
        assertTrue(noBass.diagnostics.single().contains("bass is not active"))
    }

    @Test
    fun `adapter writes a stable inspectable transition artifact without touching source midi`() {
        val source = projectRoot.resolve("source/A.mid")
        val clean = projectRoot.resolve("midi/clean/A.mid")
        Files.createDirectories(source.parent)
        Files.createDirectories(clean.parent)
        Files.writeString(source, "source MIDI remains untouched")
        writeTestMidi(clean)
        val project = Project(Project.CURRENT_VERSION, "transitions", listOf(Part("A", "source/A.mid", midi = MidiReferences(clean = "midi/clean/A.mid"))), renderFormat = RenderFormat(sampleRate = 32_000, channels = 3))
        val arrangement = DetailedArrangement(sections = listOf(
            detailedSection(0, TransitionPlan(TransitionType.BRIDGE, 1, bridge = BridgePlan(0.7, listOf(BridgeElement.DRUM_FILL)))),
            detailedSection(1, TransitionPlan())
        ))
        val before = Files.readAllBytes(source)
        val testLibrary = createTestLibrary()

        val generated = MidiTransitionGenerationAdapter(libraryRoot = testLibrary).generate(projectRoot, project, arrangement, mapOf("A" to analysis()))
        val sequence = MidiSystem.getSequence(generated.path.toFile())

        assertEquals(projectRoot.resolve("midi/generated/transitions.mid"), generated.path)
        assertTrue(generated.result.events.isNotEmpty())
        assertTrue(generated.result.events.all { it.instrument == LogicalInstrument.DRUMS })
        assertEquals(480, sequence.resolution)
        assertTrue(sequence.tickLength >= 5760)
        assertTrue(Files.readAllBytes(source).contentEquals(before))
        assertEquals(Files.readAllBytes(generated.path).toList(), Files.readAllBytes(MidiTransitionGenerationAdapter(libraryRoot = testLibrary).generate(projectRoot, project, arrangement, mapOf("A" to analysis())).path).toList())
    }

    @Test
    fun `adapter places the exact approved cohesion bridge at its shifted boundary`() {
        val source = projectRoot.resolve("source/A.mid")
        val clean = projectRoot.resolve("midi/clean/A.mid")
        Files.createDirectories(source.parent)
        Files.writeString(source, "immutable source evidence")
        writeTestMidi(clean)
        val project = Project(Project.CURRENT_VERSION, "approved-cohesion", listOf(Part("A", "source/A.mid", midi = MidiReferences(clean = "midi/clean/A.mid"))), listOf("A", "A"), RenderFormat())
        val bridge = TransitionBridgePlan("A1", "A2", "1".repeat(64), "1".repeat(64), BridgeType.DRUM_FILL, 1, "drums", HarmonicHandoff.HOLD, RhythmicGesture.FILL, EnergyContour.RISE, rationale = "Approved drum handoff")
        val approved = projectRoot.resolve(CohesionBoundaryArtifactPaths.approved("A1", "A2"))
        Files.createDirectories(approved.parent)
        Files.writeString(approved, Json.encodeToString(bridge))
        writeTestMidi(projectRoot.resolve(TransitionCohesionStore.bridgeMidi("A1", "A2")), pitch = 71)
        val arrangement = DetailedArrangement(
            cohesion = ArrangementCohesionReferences(
                "2".repeat(64),
                listOf(
                    ArrangementCohesionBoundaryReference(
                        "A1", "A2", digest(approved),
                        digest(projectRoot.resolve(TransitionCohesionStore.bridgeMidi("A1", "A2")))
                    )
                )
            ),
            sections = listOf(
                detailedSection(0, TransitionPlan(TransitionType.BRIDGE, 1, bridge = BridgePlan(0.7, listOf(BridgeElement.DRUM_FILL)))),
                detailedSection(1, TransitionPlan())
            )
        )

        val workflow = CohesionWorkflowReferences(
            "2".repeat(64), WorkflowArtifactReference("cohesion/cohesion.json", "2".repeat(64)), emptyList(), approved = true,
            boundaries = listOf(CohesionBoundaryReference("A1", "A2", "2".repeat(64), approved = WorkflowArtifactReference("cohesion/boundaries/A1--A2/boundary.json", digest(approved)), bridgeSha256 = digest(projectRoot.resolve(TransitionCohesionStore.bridgeMidi("A1", "A2")))))
        )
        val currentProject = project.copy(workflow = ProjectWorkflowReferences(cohesion = workflow))
        val generated = MidiTransitionGenerationAdapter(libraryRoot = createTestLibrary()).generate(projectRoot, currentProject, arrangement, mapOf("A" to analysis()))
        val notes = MidiSystem.getSequence(generated.path.toFile()).tracks.flatMap { track ->
            (0 until track.size()).mapNotNull { index ->
                val event = track[index]
                val message = event.message as? ShortMessage
                message?.takeIf { it.command == ShortMessage.NOTE_ON && it.data2 > 0 }?.let { event.tick to it.data1 }
            }
        }

        assertEquals(listOf(1_920L to 71), notes)
        assertEquals(listOf(1_920L), generated.result.events.map { it.startTick })

        writeTestMidi(projectRoot.resolve(TransitionCohesionStore.bridgeMidi("A1", "A2")), pitch = 72)
        assertThrows(IllegalArgumentException::class.java) {
            MidiTransitionGenerationAdapter(libraryRoot = createTestLibrary()).generate(projectRoot, currentProject, arrangement, mapOf("A" to analysis()))
        }
    }

    private fun digest(path: Path): String = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }

    private fun createTestLibrary(): Path {
        val library = projectRoot.resolve("test-library")
        Files.createDirectories(library)
        Files.writeString(library.resolve("instruments.json"), """{
            "version": 1,
            "workingSampleRate": 44100,
            "midiChannelConvention": "one-based",
            "instruments": {
                "piano": {"engine": "sfz", "path": "piano/piano.sfz", "licenseId": "starter-generated", "midiProgram": 0},
                "bass": {"engine": "sfz", "path": "bass/bass.sfz", "licenseId": "starter-generated", "midiProgram": 33},
                "drums": {"engine": "sfz", "path": "drums/drums.sfz", "licenseId": "starter-generated", "midiChannel": 10, "noteMap": {"kick": 36, "snare": 38, "clap": 39, "closedHat": 42, "openHat": 46}},
                "pad": {"engine": "sfz", "path": "pad/pad.sfz", "licenseId": "starter-generated", "midiProgram": 17},
                "strings": {"engine": "sfz", "path": "strings/strings.sfz", "licenseId": "starter-generated", "midiProgram": 48}
            }
        }""".trimIndent())
        Files.writeString(library.resolve("LICENSES.json"), """{
            "version": 1,
            "libraries": {
                "starter-generated": {
                    "displayName": "Starter Generated",
                    "source": "local",
                    "provenance": "generated-original",
                    "license": "MIT",
                    "commercialUse": true,
                    "attributionRequired": false,
                    "redistribution": "allowed"
                }
            }
        }""".trimIndent())
        val instrumentSamples = mapOf(
            "piano" to listOf("C2.wav"), "bass" to listOf("E1.wav"), "pad" to listOf("C2.wav"), "strings" to listOf("C2.wav"),
            "drums" to listOf("kick.wav", "snare.wav", "clap.wav", "hat_closed.wav", "hat_open.wav")
        )
        instrumentSamples.forEach { (instrument, samples) ->
            val directory = library.resolve(instrument)
            Files.createDirectories(directory.resolve("samples"))
            val sfz = when (instrument) {
                "drums" -> "<region> sample=samples/kick.wav key=36\n<region> sample=samples/snare.wav key=38\n<region> sample=samples/clap.wav key=39\n<region> sample=samples/hat_closed.wav key=42\n<region> sample=samples/hat_open.wav key=46\n"
                else -> "<region> sample=samples/${samples.first()} key=36\n"
            }
            Files.writeString(directory.resolve("$instrument.sfz"), sfz)
            samples.forEach { writeTestWav(directory.resolve("samples/$it")) }
        }
        return library
    }

    private fun writeTestWav(path: Path) {
        Files.createDirectories(requireNotNull(path.parent))
        java.io.DataOutputStream(java.io.FileOutputStream(path.toFile())).use { out ->
            val dataSize = 4 * 1 * 2
            out.writeBytes("RIFF"); out.writeInt(java.lang.Integer.reverseBytes(36 + dataSize)); out.writeBytes("WAVE")
            out.writeBytes("fmt "); out.writeInt(java.lang.Integer.reverseBytes(16)); out.writeShort(java.lang.Short.reverseBytes(1).toInt()); out.writeShort(java.lang.Short.reverseBytes(1).toInt()); out.writeInt(java.lang.Integer.reverseBytes(44100))
            out.writeInt(java.lang.Integer.reverseBytes(88_200)); out.writeShort(java.lang.Short.reverseBytes(2).toInt()); out.writeShort(java.lang.Short.reverseBytes(16).toInt())
            out.writeBytes("data"); out.writeInt(java.lang.Integer.reverseBytes(dataSize))
            repeat(4) { out.writeShort(java.lang.Short.reverseBytes(8192).toInt()) }
        }
    }

    private fun result(type: MidiTransitionType, bars: Int) = engine.generate(
        listOf(section(0, "A"), section(1, "B")), listOf(MidiTransitionPlan(type, bars), MidiTransitionPlan()), instruments, drumMap
    )

    private fun section(
        index: Int, part: String, duration: Long = 1920, meter: MidiTimeSignature = MidiTimeSignature(0, 4, 4),
        finalChord: MidiChord = chord("Am"), instruments: Set<LogicalInstrument> = setOf(LogicalInstrument.BASS, LogicalInstrument.DRUMS, LogicalInstrument.PAD)
    ) = TransitionSectionContext(index, part, 480, duration, listOf(MidiTempoChange(0, if (index == 1) 90.0 else 120.0)), listOf(meter), MidiKey("A", "minor", 0.8),
        listOf(finalChord.copy(startTick = 0, endTick = duration)), instruments, 0.7).let { context ->
        if (part == "B") context.copy(chords = listOf(chord("F").copy(endTick = duration))) else context
    }

    private fun detailedSection(index: Int, transition: TransitionPlan) = DetailedArrangementSection(
        index, "A${index + 1}", "A", SongSectionPurpose.DEVELOPMENT, 0.7,
        listOf(PianoSourcePlan(), BassInstrumentPlan(role = DetailedBassRole.ROOT, density = 0.7, movement = DetailedBassMovement.STATIC, register = MusicalRegister.LOW, syncopation = 0.0),
            DrumsInstrumentPlan(role = DrumsRole.BUILD, density = 0.7, kickDensity = 0.7, snarePattern = SnarePattern.BEATS_2_4, hiHatDensity = 0.7, swing = 0.0, fillLastBar = false),
            PadInstrumentPlan(role = SustainedRole.SUSTAINED, density = 0.7, register = MusicalRegister.MID)), transition
    )

    private fun analysis() = MidiAnalysis(partId = "A", ppq = 480, durationTicks = 1920, durationSeconds = 2.0, tempoMap = listOf(MidiTempoChange(0, 120.0)), timeSignatures = listOf(MidiTimeSignature(0, 4, 4)), bars = 1, beats = 4.0, noteCount = 3,
        noteDensity = 0.1, rhythmicDensity = 0.1, energy = 0.7, key = MidiKey("A", "minor", 0.8), chords = listOf(chord("Am").copy(endTick = 1920)))
    private fun chord(symbol: String, confidence: Double = 0.9) = MidiChord(0, 1920, symbol, confidence)

    private val instruments = mapOf(LogicalInstrument.BASS to TransitionInstrument(0, 32), LogicalInstrument.DRUMS to TransitionInstrument(9), LogicalInstrument.PAD to TransitionInstrument(1, 89))
    private val drumMap = mapOf("kick" to 36, "snare" to 38, "clap" to 39, "closedHat" to 42, "openHat" to 46)
}
