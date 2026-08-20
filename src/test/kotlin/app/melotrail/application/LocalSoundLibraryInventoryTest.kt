package app.melotrail.application

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalSoundLibraryInventoryTest {
    @TempDir lateinit var temp: Path

    @Test
    fun `validated registry produces stable UI safe instrument metadata and deterministic filters`() {
        val root = fixture()

        val inventory = RegistryLocalSoundLibraryInventoryReader.read(root)

        assertEquals(LocalSoundLibraryInventoryState.READY, inventory.state)
        assertEquals(listOf("bass", "drums", "pad", "piano", "strings"), inventory.instruments.map { it.id })
        val piano = inventory.instruments.single { it.id == "piano" }
        assertEquals("Piano", piano.name)
        assertEquals(1, piano.sampleCount)
        assertEquals("Fixture Library", piano.licenseName)
        assertEquals("fixture-source", piano.source)
        assertTrue(piano.attributionRequired)
        assertEquals(listOf("bass", "drums", "pad", "piano", "strings"), inventory.filtered("fixture-source", "instrument").map { it.id })
        assertEquals(listOf("bass"), inventory.filtered("  BASS ", null).map { it.id })
    }

    @Test
    fun `unconfigured invalid root missing sfz and missing samples never expose inventory cards`() {
        assertEquals(LocalSoundLibraryInventoryState.UNCONFIGURED, RegistryLocalSoundLibraryInventoryReader.read(null).state)
        assertEquals(LocalSoundLibraryInventoryState.INVALID, RegistryLocalSoundLibraryInventoryReader.read(temp.resolve("missing")).state)

        val noSfz = fixture(temp.resolve("no-sfz"))
        Files.delete(noSfz.resolve("piano/piano.sfz"))
        val missingSfz = RegistryLocalSoundLibraryInventoryReader.read(noSfz)
        assertEquals(LocalSoundLibraryInventoryState.INVALID, missingSfz.state)
        assertTrue(missingSfz.instruments.isEmpty())

        val noSample = fixture(temp.resolve("no-sample"))
        Files.delete(noSample.resolve("piano/samples/piano.wav"))
        val missingSample = RegistryLocalSoundLibraryInventoryReader.read(noSample)
        assertEquals(LocalSoundLibraryInventoryState.INVALID, missingSample.state)
        assertTrue(missingSample.instruments.isEmpty())
        assertTrue(missingSample.recoveryMessage.orEmpty().contains("sample", ignoreCase = true))
    }

    private fun fixture(root: Path = temp.resolve("library")): Path {
        val standard = listOf("piano" to 60, "bass" to 48, "pad" to 60, "strings" to 60)
        standard.forEach { (name, key) ->
            writeSample(root.resolve("$name/samples/$name.wav"))
            Files.createDirectories(root.resolve(name))
            Files.writeString(root.resolve("$name/$name.sfz"), "<region> sample=samples/$name.wav key=$key")
        }
        val drumHits = listOf("kick" to 36, "snare" to 38, "clap" to 39, "hat_closed" to 42, "hat_open" to 46)
        drumHits.forEach { (name, _) -> writeSample(root.resolve("drums/samples/$name.wav")) }
        Files.createDirectories(root.resolve("drums"))
        Files.writeString(root.resolve("drums/drums.sfz"), drumHits.joinToString("\n") { (name, key) -> "<region> sample=samples/$name.wav key=$key" })
        Files.writeString(root.resolve("LICENSES.json"), """{"version":1,"libraries":{"fixture":{"displayName":"Fixture Library","source":"fixture-source","provenance":"generated-original","license":"fixture-license","commercialUse":true,"attributionRequired":true,"attributionText":"Fixture credit","redistribution":"allowed"}}}""")
        Files.writeString(root.resolve("instruments.json"), """{"version":1,"workingSampleRate":44100,"midiChannelConvention":"one-based","instruments":{"piano":{"engine":"sfz","path":"piano/piano.sfz","licenseId":"fixture","midiProgram":0},"bass":{"engine":"sfz","path":"bass/bass.sfz","licenseId":"fixture","midiProgram":32},"drums":{"engine":"sfz","path":"drums/drums.sfz","licenseId":"fixture","midiChannel":10,"noteMap":{"kick":36,"snare":38,"clap":39,"closedHat":42,"openHat":46}},"pad":{"engine":"sfz","path":"pad/pad.sfz","licenseId":"fixture","midiProgram":89},"strings":{"engine":"sfz","path":"strings/strings.sfz","licenseId":"fixture","midiProgram":48}}}""")
        return root
    }

    private fun writeSample(path: Path) {
        Files.createDirectories(path.parent)
        val data = byteArrayOf(0, 0)
        val bytes = ByteBuffer.allocate(44 + data.size).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray()).putInt(36 + data.size).put("WAVEfmt ".toByteArray()).putInt(16)
        bytes.putShort(1).putShort(1).putInt(44_100).putInt(88_200).putShort(2).putShort(16)
        bytes.put("data".toByteArray()).putInt(data.size).put(data)
        Files.write(path, bytes.array())
    }
}
