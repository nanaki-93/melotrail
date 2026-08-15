package app.melotrail.arrangement

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage

class SfizzInstrumentRendererTest {
    @TempDir lateinit var root: Path

    @Test
    fun `uses registry resolved SFZ path propagates the requested rate and publishes PCM24 atomically`() = runBlocking {
        val library = createLibrary()
        val midi = midiFile()
        var received = emptyList<String>()
        val renderer = renderer(library) { arguments ->
            received = arguments
            writePcmWav(Path.of(arguments[arguments.indexOf("--wav") + 1]), 32_000, 2, 80)
            success()
        }
        val output = root.resolve("output/stem.wav")

        val result = renderer.render(midi, LogicalInstrument.BASS, output, RenderFormat(32_000, 3, 24), 100)

        assertEquals(listOf(
            "fake-sfizz", "--sfz", library.resolve("bass/bass.sfz").toString(), "--midi", midi.toAbsolutePath().normalize().toString(),
            "--wav", received[received.indexOf("--wav") + 1], "--samplerate", "32000", "--use-eot"
        ), received)
        assertEquals(output.toAbsolutePath().normalize(), result.output)
        assertEquals(32_000, result.sampleRate)
        assertEquals(3, result.channels)
        assertEquals(24, result.bitDepth)
        assertEquals(100, result.frameCount)
        val bytes = Files.readAllBytes(output)
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(3, header.getShort(22).toInt())
        assertEquals(32_000, header.getInt(24))
        assertEquals(24, header.getShort(34).toInt())
        assertEquals(100L * 3 * 3, header.getInt(40).toLong())
        assertFalse(Files.list(output.parent).use { files -> files.anyMatch { it.fileName.toString().contains("sfizz-") } })
    }

    @Test
    fun `reports process failures and timeouts without a partial final output`() = runBlocking {
        val library = createLibrary()
        val midi = midiFile()
        val output = root.resolve("failed.wav")
        Files.writeString(output, "previous-good-output")
        val failed = renderer(library) { RendererProcessResult(7, false, "renderer output", "renderer error") }

        val failure = assertThrows(IllegalStateException::class.java) {
            runBlocking { failed.render(midi, LogicalInstrument.PIANO, output, RenderFormat(44_100, 2, 24), 10) }
        }
        assertTrue(failure.message.orEmpty().contains("exit code 7"))
        assertTrue(failure.message.orEmpty().contains("renderer error"))
        assertEquals("previous-good-output", Files.readString(output))

        val timedOut = renderer(library) { RendererProcessResult(null, true, "", "stalled") }
        val timeout = assertThrows(IllegalStateException::class.java) {
            runBlocking { timedOut.render(midi, LogicalInstrument.PIANO, output, RenderFormat(44_100, 2, 24), 10) }
        }
        assertTrue(timeout.message.orEmpty().contains("timed out"))
        assertEquals("previous-good-output", Files.readString(output))
    }

    @Test
    fun `rejects malformed or invalid renderer WAVs and never publishes them`() = runBlocking {
        val library = createLibrary()
        val midi = midiFile()
        val cases = listOf<Pair<String, (Path) -> Unit>>(
            "malformed" to { Files.write(it, byteArrayOf(1, 2, 3)) },
            "empty" to { writePcmWav(it, 44_100, 2, 0) },
            "wrong-rate" to { writePcmWav(it, 48_000, 2, 4) },
            "wrong-channel" to { writePcmWav(it, 44_100, 1, 4) },
            "clipped" to { writePcmWav(it, 44_100, 2, 4, -1.0) },
            "non-finite" to { writeFloatWav(it, 44_100, 2, floatArrayOf(Float.NaN, 0f)) },
            "too-long" to { writePcmWav(it, 8_000, 2, 16_011) }
        )

        cases.forEach { (name, writer) ->
            val output = root.resolve("$name.wav")
            val renderer = renderer(library) { arguments -> writer(Path.of(arguments[arguments.indexOf("--wav") + 1])); success() }
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    renderer.render(midi, LogicalInstrument.PIANO, output, RenderFormat(if (name == "too-long") 8_000 else 44_100, 2, 24), 10)
                }
            }
            assertFalse(Files.exists(output), "$name must not publish an output")
        }
    }

    @Test
    fun `pads short output and blocks output inside source sound library`() = runBlocking {
        val library = createLibrary()
        val midi = midiFile()
        val renderer = renderer(library) { arguments -> writePcmWav(Path.of(arguments[arguments.indexOf("--wav") + 1]), 44_100, 2, 4); success() }
        val output = root.resolve("padded.wav")
        renderer.render(midi, LogicalInstrument.PIANO, output, RenderFormat(44_100, 1, 24), 10)
        val data = Files.readAllBytes(output).copyOfRange(44, Files.size(output).toInt())
        assertArrayEquals(ByteArray(18), data.copyOfRange(data.size - 18, data.size))

        val unsafe = library.resolve("piano/unsafe.wav")
        val exception = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { renderer.render(midi, LogicalInstrument.PIANO, unsafe, RenderFormat(44_100, 2, 24), 10) }
        }
        assertTrue(exception.message.orEmpty().contains("sound library"))

        val midiNamedWav = root.resolve("validated-midi.wav")
        Files.copy(midi, midiNamedWav)
        val sourceOverwrite = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { renderer.render(midiNamedWav, LogicalInstrument.PIANO, midiNamedWav, RenderFormat(44_100, 2, 24), 10) }
        }
        assertTrue(sourceOverwrite.message.orEmpty().contains("must not overwrite source"))
    }

    @Test
    fun `renders starter piano and bass when sfizz renderer is explicitly available`() = runBlocking {
        val executable = configuredRenderer() ?: run {
            assumeTrue(false, "sfizz_render unavailable; set SFZ_RENDERER_PATH or install sfizz_render to run starter-library integration")
            return@runBlocking
        }
        val samples = Path.of("sounds/piano/samples/C2.wav")
        assertTrue(Files.isRegularFile(samples), "starter-library asset is missing; this is not a renderer-availability skip")
        val midi = midiFile()
        listOf(LogicalInstrument.PIANO, LogicalInstrument.BASS).forEach { instrument ->
            val output = root.resolve("${instrument.wireName}.wav")
            val result = SfizzInstrumentRenderer(InstrumentRegistryLoader(Path.of("sounds")), executable = executable).render(midi, instrument, output, RenderFormat(44_100, 2, 24), 44_100)
            assertEquals(44_100, result.sampleRate)
            assertEquals(2, result.channels)
            assertEquals(24, result.bitDepth)
            assertEquals(44_100, result.frameCount)
            assertTrue(result.peak > 0.0)
        }
    }

    private fun renderer(library: Path, action: (List<String>) -> RendererProcessResult): SfizzInstrumentRenderer =
        SfizzInstrumentRenderer(InstrumentRegistryLoader(library), RendererProcess { args, _ -> action(args) }, "fake-sfizz", "test")

    private fun success() = RendererProcessResult(0, false, "ok", "")

    private fun createLibrary(): Path {
        val library = root.resolve("library")
        Files.createDirectories(library)
        Files.copy(Path.of("sounds/instruments.json"), library.resolve("instruments.json"))
        Files.copy(Path.of("sounds/LICENSES.json"), library.resolve("LICENSES.json"))
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
            samples.forEach { writePcmWav(directory.resolve("samples/$it"), 44_100, 1, 4) }
        }
        return library
    }

    private fun midiFile(): Path {
        val path = root.resolve("input.mid")
        val sequence = Sequence(Sequence.PPQ, 480)
        val track = sequence.createTrack()
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, 60, 96), 0))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, 60, 0), 240))
        MidiSystem.write(sequence, 1, path.toFile())
        return path
    }

    private fun configuredRenderer(): String? {
        val explicit = System.getenv("SFZ_RENDERER_PATH")?.takeIf { it.isNotBlank() }
        if (explicit != null) return explicit.takeIf { Files.isExecutable(Path.of(it)) }
        return System.getenv("PATH").orEmpty().split(':')
            .map { Path.of(it).resolve("sfizz_render") }
            .firstOrNull(Files::isExecutable)
            ?.toString()
    }

    private fun writePcmWav(path: Path, sampleRate: Int, channels: Int, frames: Int, sample: Double = 0.25) {
        Files.createDirectories(requireNotNull(path.parent))
        DataOutputStream(Files.newOutputStream(path)).use { out ->
            val dataSize = frames * channels * 2
            out.writeBytes("RIFF"); out.writeIntLE(36 + dataSize); out.writeBytes("WAVE")
            out.writeBytes("fmt "); out.writeIntLE(16); out.writeShortLE(1); out.writeShortLE(channels); out.writeIntLE(sampleRate)
            out.writeIntLE(sampleRate * channels * 2); out.writeShortLE(channels * 2); out.writeShortLE(16)
            out.writeBytes("data"); out.writeIntLE(dataSize)
            repeat(frames * channels) { out.writeShortLE((sample * 32768.0).toInt().coerceIn(-32768, 32767)) }
        }
    }

    private fun writeFloatWav(path: Path, sampleRate: Int, channels: Int, samples: FloatArray) {
        DataOutputStream(Files.newOutputStream(path)).use { out ->
            out.writeBytes("RIFF"); out.writeIntLE(36 + samples.size * 4); out.writeBytes("WAVE")
            out.writeBytes("fmt "); out.writeIntLE(16); out.writeShortLE(3); out.writeShortLE(channels); out.writeIntLE(sampleRate)
            out.writeIntLE(sampleRate * channels * 4); out.writeShortLE(channels * 4); out.writeShortLE(32)
            out.writeBytes("data"); out.writeIntLE(samples.size * 4)
            samples.forEach { out.writeIntLE(it.toRawBits()) }
        }
    }

    private fun DataOutputStream.writeIntLE(value: Int) { writeByte(value); writeByte(value ushr 8); writeByte(value ushr 16); writeByte(value ushr 24) }
    private fun DataOutputStream.writeShortLE(value: Int) { writeByte(value); writeByte(value ushr 8) }
}
