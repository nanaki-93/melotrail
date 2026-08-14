package ai.music.workstation.application

import ai.music.workstation.arrangement.InstrumentRenderer
import ai.music.workstation.arrangement.LogicalInstrument
import ai.music.workstation.arrangement.Project
import ai.music.workstation.arrangement.ProjectStore
import ai.music.workstation.arrangement.RenderFormat
import ai.music.workstation.arrangement.RenderResult
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PartPreviewApplicationServiceTest {
    @TempDir lateinit var root: Path

    @Test fun `valid wav source is reused without mutation`() = runTest {
        val source = writeWav(root.resolve("parts/A.wav"), 16)
        project("parts/A.wav")
        val before = Files.readAllBytes(source)

        val result = service().resolve(PreviewRequest(root, "A"))

        val resolved = assertIs<PreviewResult.Resolved>(result)
        assertEquals(source, resolved.artifact)
        assertTrue(resolved.reused)
        assertTrue(before.contentEquals(Files.readAllBytes(source)))
    }

    @Test fun `mp3 decode is cached invalidated and corrupt cache is replaced`() = runTest {
        val source = root.resolve("parts/A.mp3").also { Files.createDirectories(it.parent); Files.writeString(it, "mp3-one") }
        project("parts/A.mp3")
        val decoder = FakeMp3Decoder()
        val service = service(decoder)

        val first = assertIs<PreviewResult.Resolved>(service.resolve(PreviewRequest(root, "A")))
        val second = assertIs<PreviewResult.Resolved>(service.resolve(PreviewRequest(root, "A")))
        assertFalse(first.reused); assertTrue(second.reused); assertEquals(1, decoder.calls)
        Files.writeString(first.artifact, "corrupt")
        assertFalse(assertIs<PreviewResult.Resolved>(service.resolve(PreviewRequest(root, "A"))).reused)
        Files.writeString(source, "mp3-two")
        assertFalse(assertIs<PreviewResult.Resolved>(service.resolve(PreviewRequest(root, "A"))).reused)
        assertEquals(3, decoder.calls)
    }

    @Test fun `missing clean MIDI analysis is a typed prerequisite`() = runTest {
        val midi = root.resolve("parts/A.mid").also { Files.createDirectories(it.parent); Files.write(it, byteArrayOf()) }
        ProjectStore.write(root, Project(name = "test", parts = listOf(ai.music.workstation.arrangement.Part("A", "parts/A.mid"))))

        val result = service().resolve(PreviewRequest(root, "A"))

        assertIs<PreviewResult.Prerequisite>(result)
    }

    private fun project(file: String) = ProjectStore.write(root, Project(name = "test", parts = listOf(ai.music.workstation.arrangement.Part("A", file))))
    private fun service(decoder: PreviewMp3Decoder = FakeMp3Decoder()) = DefaultPartPreviewApplicationService(FakeRenderer(), decoder)

    private class FakeMp3Decoder : PreviewMp3Decoder {
        override val configurationFingerprint = "fake-v1"
        var calls = 0
        override suspend fun decode(source: Path, output: Path) { calls++; writeWav(output, 24) }
    }
    private class FakeRenderer : InstrumentRenderer {
        override suspend fun render(midi: Path, instrument: LogicalInstrument, output: Path, format: RenderFormat, expectedFrames: Long): RenderResult = error("not used")
    }
}

private fun writeWav(path: Path, bits: Int): Path {
    Files.createDirectories(path.parent)
    val bytes = if (bits == 24) 3 else 2
    val data = ByteArray(bytes)
    Files.newOutputStream(path).use { out ->
        fun i(value: Int) { out.write(value); out.write(value shr 8); out.write(value shr 16); out.write(value shr 24) }
        fun s(value: Int) { out.write(value); out.write(value shr 8) }
        out.write("RIFF".toByteArray()); i(36 + data.size); out.write("WAVEfmt ".toByteArray()); i(16); s(1); s(1); i(44_100); i(44_100 * bytes); s(bytes); s(bits); out.write("data".toByteArray()); i(data.size); out.write(data)
    }
    return path
}
