package ai.music.workstation.arrangement

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class InstrumentRegistryTest {
    @TempDir lateinit var root: Path

    @Test
    fun `validates the current five-instrument starter library and normalizes drum channel`() {
        val registry = InstrumentRegistryLoader(Path.of("sounds")).load()
        assertEquals(setOf("piano", "bass", "drums", "pad", "strings"), registry.logicalNames())
        assertEquals(9, registry.resolve("drums").midiChannelZeroBased)
        assertEquals(5, registry.resolve("drums").samplePaths.size)
        assertTrue(registry.resolve("piano").samplePaths.all(Files::isRegularFile))
    }

    @Test
    fun `rejects path traversal missing license invalid channel and drum mapping`() {
        copyLibrary()
        replace("\"piano/piano.sfz\"", "\"../piano.sfz\"")
        assertTrue(failure().contains("must be relative"))

        copyLibrary(); replace("\"licenseId\": \"starter-generated\"", "\"licenseId\": \"missing\"")
        assertTrue(failure().contains("missing license"))

        copyLibrary(); replace("\"midiChannel\": 10", "\"midiChannel\": 17")
        assertTrue(failure().contains("one-based 1..16"))

        copyLibrary(); replace("\"kick\": 36", "\"kick\": 35")
        assertTrue(failure().contains("disagrees"))
    }

    @Test
    fun `rejects malformed SFZ missing sample wrong WAV rate and symlink escape`() {
        copyLibrary()
        Files.writeString(root.resolve("piano/piano.sfz"), "<region> key=60")
        assertTrue(failure().contains("without sample"))

        copyLibrary()
        Files.delete(root.resolve("piano/samples/C2.wav"))
        assertTrue(failure().contains("does not exist"))

        copyLibrary()
        val wave = root.resolve("piano/samples/C2.wav")
        val bytes = Files.readAllBytes(wave); bytes[24] = 0; bytes[25] = 0; bytes[26] = 0; bytes[27] = 0
        Files.write(wave, bytes)
        assertTrue(failure().contains("invalid WAV format"))

        copyLibrary()
        val outside = root.parent.resolve("outside.wav").also { Files.write(it, Files.readAllBytes(root.resolve("piano/samples/C2.wav"))) }
        Files.delete(root.resolve("piano/samples/C2.wav")); Files.createSymbolicLink(root.resolve("piano/samples/C2.wav"), outside)
        assertTrue(failure().contains("escapes"))
    }

    private fun failure(): String = assertThrows(IllegalArgumentException::class.java) { InstrumentRegistryLoader(root).load() }.message.orEmpty()
    private fun replace(old: String, new: String) {
        val file = root.resolve("instruments.json")
        Files.writeString(file, Files.readString(file).replace(old, new))
    }
    private fun copyLibrary() {
        Files.walk(Path.of("sounds")).use { paths -> paths.forEach { source ->
            val target = root.resolve(Path.of("sounds").relativize(source).toString())
            if (Files.isDirectory(source)) Files.createDirectories(target) else Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
        } }
    }
}
