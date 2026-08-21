package app.melotrail.arrangement

import app.melotrail.audio.AudioBuffer
import app.melotrail.audio.AudioFormat
import app.melotrail.audio.WAVDecoder
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.FileVisitResult
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage

class StemRenderingMixerTest {
    @TempDir lateinit var root: Path

    @Test
    fun `renders aligned project-format stems including a transition and reuses only current artifacts`() = runBlocking {
        val analyses = mapOf("A" to analysis("A"), "B" to analysis("B"))
        writeMidi(root.resolve("source/A.mid"), 0, 1_920)
        writeMidi(root.resolve("source/B.mid"), 0, 1_920)
        Files.createDirectories(root.resolve("midi/clean"))
        Files.copy(root.resolve("source/A.mid"), root.resolve("midi/clean/A.mid"))
        Files.copy(root.resolve("source/B.mid"), root.resolve("midi/clean/B.mid"))
        val project = project()
        writeMidi(root.resolve("midi/generated/bass.mid"), 0, 3_840)
        writeMidi(root.resolve("midi/generated/transitions.mid"), 1_920, 2_040)
        ProjectStore.write(root, project)

        val renderer = FakeRenderer()
        val service = StemRenderingMixer(renderer, libraryRoot = TestSoundLibrary.root())
        val first = service.render(root, project, arrangement(), analyses)

        assertFalse(first.reused)
        assertEquals(2, renderer.calls)
        assertEquals(48_000, first.report.timelineFrames) // two 2-second sections plus one 2-second inserted 4/4 bar
        assertEquals(listOf("piano", "bass"), first.report.stems.map { it.name })
        assertEquals(2, first.report.version)
        assertEquals(listOf("bass", "piano"), first.report.instruments.map { it.role })
        assertTrue(first.report.instruments.all { it.legacyAlias && it.stableInstrumentId == it.role && it.assets.isNotEmpty() })
        assertEquals(first.report.stems.map { it.name }, first.report.stems.map { it.stableInstrumentId })
        listOf("stems/piano.wav", "stems/bass.wav", "mix/dry.wav").forEach { relative ->
            val audio = WAVDecoder(QuietReporter).decode(root.resolve(relative))
            assertEquals(8_000, audio.format.sampleRate)
            assertEquals(1, audio.format.channels)
            assertEquals(24, audio.format.bitDepth)
            assertEquals(48_000, audio.length)
        }
        assertTrue(first.report.predictedPeak > 0.95f)
        assertTrue(first.report.appliedGain < 1f)
        assertTrue(WAVDecoder(QuietReporter).decode(root.resolve("mix/dry.wav")).samples.all { kotlin.math.abs(it) <= 0.9501f })

        assertTrue(service.render(root, project, arrangement(), analyses).reused)
        assertEquals(2, renderer.calls)
        Files.write(root.resolve("stems/bass.wav"), byteArrayOf(0))
        assertFalse(service.render(root, project, arrangement(), analyses).reused)
        assertEquals(4, renderer.calls)
        Files.write(root.resolve("midi/generated/bass.mid"), byteArrayOf(0), java.nio.file.StandardOpenOption.APPEND)
        assertFalse(service.render(root, project, arrangement(), analyses).reused)
        assertEquals(6, renderer.calls)
    }

    @Test
    fun `mixer uses a uniform reduction instead of hard clipping and rejects non-finite samples`() {
        val mixer = DeterministicStemMixer()
        val loud = AudioBuffer(floatArrayOf(0.8f, 0.8f), AudioFormat(44_100, 1, 24, false, false, "WAV"), 2.0 / 44_100)
        val mixed = mixer.mix(listOf(MixTrack("a", loud), MixTrack("b", loud)))
        assertEquals(1.6f, mixed.predictedPeak)
        assertEquals(0.95f, mixed.buffer.samples[0], 0.0001f)
        assertTrue(mixed.appliedGain < 1f)
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            mixer.mix(listOf(MixTrack("bad", loud.copy(samples = floatArrayOf(Float.NaN, 0f)))))
        }
    }

    @Test
    fun `bridge uses the incoming tempo before generated MIDI resumes`() = runBlocking {
        val analyses = mapOf("A" to analysis("A", bpm = 120.0), "B" to analysis("B", bpm = 90.0))
        writeMidi(root.resolve("source/A.mid"), 0, 1_920)
        writeMidi(root.resolve("source/B.mid"), 0, 1_920)
        Files.createDirectories(root.resolve("midi/clean"))
        Files.copy(root.resolve("source/A.mid"), root.resolve("midi/clean/A.mid"))
        Files.copy(root.resolve("source/B.mid"), root.resolve("midi/clean/B.mid"))
        val project = project()
        writeMidi(root.resolve("midi/generated/bass.mid"), 0, 3_840)
        writeMidi(root.resolve("midi/generated/transitions.mid"), 1_920, 2_040)

        val renderer = FakeRenderer()
        StemRenderingMixer(renderer, libraryRoot = TestSoundLibrary.root()).render(root, project, arrangement(), analyses)

        val bass = requireNotNull(renderer.sequences[LogicalInstrument.BASS])
        assertEquals(90.0, tempos(bass).getValue(1_920L), 0.001)
        assertEquals(90.0, tempos(bass).getValue(3_840L), 0.001)
    }

    @Test
    fun `v2 render manifest uses approved stable IDs and blocks a changed registry instead of substituting`() = runBlocking {
        val library = root.resolve("library")
        copyLibrary(library)
        writeV2Catalog(library)
        val analyses = mapOf("A" to analysis("A"), "B" to analysis("B"))
        writeMidi(root.resolve("source/A.mid"), 0, 1_920)
        writeMidi(root.resolve("source/B.mid"), 0, 1_920)
        Files.createDirectories(root.resolve("midi/clean"))
        Files.copy(root.resolve("source/A.mid"), root.resolve("midi/clean/A.mid"))
        Files.copy(root.resolve("source/B.mid"), root.resolve("midi/clean/B.mid"))
        val project = project().copy(envelope = ProjectV4Envelope(arrangementAssignments = listOf(
            assignment("A1", "fixture-piano"), assignment("A1", "fixture-bass"),
            assignment("B1", "fixture-piano"), assignment("B1", "fixture-bass")
        )))
        writeMidi(root.resolve("midi/generated/bass.mid"), 0, 3_840)

        val renderer = StableIdRenderer()
        val service = StemRenderingMixer(renderer, library)
        val first = service.render(root, project, flatArrangement(), analyses)

        assertEquals(listOf("fixture-bass", "fixture-piano"), first.report.instruments.map { it.stableInstrumentId })
        assertEquals(setOf("fixture-piano", "fixture-bass"), renderer.stableIds.toSet())
        assertTrue(first.report.instruments.all { !it.legacyAlias && it.decisionSha256.size == 2 })

        Files.writeString(library.resolve("instruments.json"), Files.readString(library.resolve("instruments.json")).replace("Fixture pack", "Changed pack"))
        val error = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            runBlocking { service.render(root, project, flatArrangement(), analyses) }
        }
        assertTrue(error.message.orEmpty().contains("registry changed"))
    }

    private fun project() = Project(
        name = "render",
        parts = listOf(
            Part("A", "source/A.mid", midi = canonicalMidiReferences(root, "A")),
            Part("B", "source/B.mid", midi = canonicalMidiReferences(root, "B"))
        ),
        renderFormat = RenderFormat(8_000, 1, 24),
        envelope = ProjectV4Envelope(structureOccurrences = listOf(StructureOccurrence("A1", "A"), StructureOccurrence("B1", "B")))
    )

    private fun arrangement() = DetailedArrangement(sections = listOf(
        DetailedArrangementSection(0, "A1", "A", SongSectionPurpose.DEVELOPMENT, 0.3, listOf(PianoSourcePlan(), BassInstrumentPlan(role = DetailedBassRole.ROOT, density = 0.4, movement = DetailedBassMovement.STATIC, register = MusicalRegister.LOW, syncopation = 0.0)), TransitionPlan(TransitionType.BRIDGE, 1)),
        DetailedArrangementSection(1, "B1", "B", SongSectionPurpose.CLIMAX, 0.7, listOf(PianoSourcePlan(), BassInstrumentPlan(role = DetailedBassRole.ROOT, density = 0.6, movement = DetailedBassMovement.ROOT_MOTION, register = MusicalRegister.LOW, syncopation = 0.0)), TransitionPlan())
    ))

    private fun flatArrangement() = DetailedArrangement(sections = listOf(
        DetailedArrangementSection(0, "A1", "A", SongSectionPurpose.DEVELOPMENT, 0.3, listOf(PianoSourcePlan(), BassInstrumentPlan(role = DetailedBassRole.ROOT, density = 0.4, movement = DetailedBassMovement.STATIC, register = MusicalRegister.LOW, syncopation = 0.0)), TransitionPlan()),
        DetailedArrangementSection(1, "B1", "B", SongSectionPurpose.CLIMAX, 0.7, listOf(PianoSourcePlan(), BassInstrumentPlan(role = DetailedBassRole.ROOT, density = 0.6, movement = DetailedBassMovement.ROOT_MOTION, register = MusicalRegister.LOW, syncopation = 0.0)), TransitionPlan())
    ))

    private fun assignment(occurrenceId: String, instrumentId: String): ArrangementAssignmentReference = ArrangementAssignmentReference(
        occurrenceId = occurrenceId,
        instrumentId = instrumentId,
        decisionSha256 = sha256("$occurrenceId|$instrumentId".toByteArray()),
        libraryProvenance = LibraryProvenanceSnapshot(
            libraryId = "fixture-pack",
            licenseSha256 = sha256("Fixture|Fixture|third-party|CC0-1.0|true|false||unknown".toByteArray()),
            provenanceSha256 = sha256("fixture-pack|Fixture pack|1|fixture source".toByteArray())
        )
    )

    private fun writeV2Catalog(library: Path) {
        val license = """"license":{"id":"CC0-1.0","commercialUse":true,"attributionRequired":false,"sourceName":"Fixture","licenseText":"CC0 evidence"}"""
        val provenance = """"library":{"id":"fixture-pack","name":"Fixture pack","version":"1","source":"fixture source"}"""
        fun entry(id: String, name: String, role: String, path: String) = """{"id":"$id","name":"$name","roles":["$role"],"engine":{"type":"sfz","path":"$path"},$license,$provenance}"""
        Files.writeString(library.resolve("instruments.json"), """{"version":2,"workingSampleRate":44100,"instruments":[${entry("fixture-piano", "Fixture Piano", "melody", "piano/piano.sfz")},${entry("fixture-bass", "Fixture Bass", "bass", "bass/bass.sfz")}] }""")
    }

    private fun copyLibrary(destination: Path) {
        val source = TestSoundLibrary.root()
        Files.walkFileTree(source, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(directory: Path, attributes: BasicFileAttributes): FileVisitResult {
                Files.createDirectories(destination.resolve(source.relativize(directory).toString()))
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult {
                Files.copy(file, destination.resolve(source.relativize(file).toString()), StandardCopyOption.REPLACE_EXISTING)
                return FileVisitResult.CONTINUE
            }
        })
    }

    private fun analysis(id: String, bpm: Double = 120.0) = MidiAnalysis(partId = id, ppq = 480, durationTicks = 1_920, durationSeconds = 240.0 / bpm,
        tempoMap = listOf(MidiTempoChange(0, bpm)), timeSignatures = listOf(MidiTimeSignature(0, 4, 4)), bars = 1, beats = 4.0,
        noteCount = 1, noteDensity = 0.1, rhythmicDensity = 0.1, energy = 0.5)

    private fun writeMidi(path: Path, start: Long, end: Long) {
        Files.createDirectories(requireNotNull(path.parent))
        val sequence = Sequence(Sequence.PPQ, 480)
        val track = sequence.createTrack()
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, 48, 96), start))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, 48, 0), end))
        MidiSystem.write(sequence, 1, path.toFile())
    }

    private fun noteOnPitches(sequence: Sequence): List<Int> = sequence.tracks.flatMap { track ->
        (0 until track.size()).mapNotNull { index ->
            val message = track[index].message as? ShortMessage
            message?.takeIf { it.command == ShortMessage.NOTE_ON && it.data2 > 0 }?.data1
        }
    }.sorted()

    private fun sha256(path: Path): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }

    private fun sha256(value: ByteArray): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(value).joinToString("") { "%02x".format(it) }

    private class FakeRenderer : InstrumentRenderer {
        var calls = 0
        val sequences = mutableMapOf<LogicalInstrument, Sequence>()
        override suspend fun render(midi: Path, instrument: LogicalInstrument, output: Path, format: RenderFormat, expectedFrames: Long): RenderResult {
            calls++
            sequences[instrument] = MidiSystem.getSequence(midi.toFile())
            val sample = if (instrument == LogicalInstrument.PIANO) 0.8f else 0.8f
            val audio = AudioBuffer(FloatArray(expectedFrames.toInt() * format.channels) { sample }, AudioFormat(format.sampleRate, format.channels, 24, false, false, "WAV"), expectedFrames.toDouble() / format.sampleRate)
            DeterministicStemMixer().writeWav(MixedStem(audio, listOf(instrument.wireName)), output)
            return RenderResult(output, format.sampleRate, format.channels, 24, expectedFrames, audio.duration, sample.toDouble(), "fake", "test", "", "")
        }
    }

    private class StableIdRenderer : InstrumentRenderer {
        val stableIds = mutableListOf<String>()

        override suspend fun render(midi: Path, instrument: LogicalInstrument, output: Path, format: RenderFormat, expectedFrames: Long): RenderResult = error("Stable IDs are required")

        override suspend fun render(midi: Path, instrumentId: String, output: Path, format: RenderFormat, expectedFrames: Long): RenderResult {
            stableIds += instrumentId
            val audio = AudioBuffer(FloatArray(expectedFrames.toInt() * format.channels) { 0.2f }, AudioFormat(format.sampleRate, format.channels, 24, false, false, "WAV"), expectedFrames.toDouble() / format.sampleRate)
            DeterministicStemMixer().writeWav(MixedStem(audio, listOf(instrumentId)), output)
            return RenderResult(output, format.sampleRate, format.channels, 24, expectedFrames, audio.duration, 0.2, "fake", "test", "", "")
        }
    }

    private fun tempos(sequence: Sequence): Map<Long, Double> = buildMap {
        sequence.tracks.first().let { track ->
            (0 until track.size()).map(track::get).forEach { event ->
                val message = event.message as? javax.sound.midi.MetaMessage ?: return@forEach
                if (message.type == 0x51) {
                    val data = message.data
                    val micros = ((data[0].toInt() and 0xff) shl 16) or
                        ((data[1].toInt() and 0xff) shl 8) or
                        (data[2].toInt() and 0xff)
                    put(event.tick, 60_000_000.0 / micros)
                }
            }
        }
    }

    private object QuietReporter : app.melotrail.model.ErrorReporter { override fun report(message: String) = Unit; override fun report(message: String, cause: Throwable) = Unit }
}
