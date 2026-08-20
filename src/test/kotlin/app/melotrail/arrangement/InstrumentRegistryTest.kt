package app.melotrail.arrangement

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path

class InstrumentRegistryTest {
    @TempDir lateinit var root: Path

    @Test
    fun `validates the v1 compatibility fixture and normalizes drum channel`() {
        copyLibrary()
        val registry = InstrumentRegistryLoader(root).load()
        assertEquals(setOf("piano", "bass", "drums", "pad", "strings"), registry.logicalNames())
        assertEquals(9, registry.resolve("drums").midiChannelZeroBased)
        assertEquals(5, registry.resolve("drums").samplePaths.size)
        assertTrue(registry.resolve("piano").samplePaths.all(Files::isRegularFile))
    }

    @Test
    fun `v2 drum kit derives a verified standard map from its playable SFZ notes`() {
        copyLibrary()
        Files.writeString(root.resolve("instruments.json"), """{
            "version":2,"workingSampleRate":44100,"midiChannelConvention":"one-based",
            "instruments":[{
                "id":"fixture-drum-kit","name":"Fixture drum kit","roles":["drums"],
                "engine":{"type":"sfz","path":"drums/drums.sfz"},"midiChannel":10,
                "license":{"id":"CC0-1.0","commercialUse":true,"attributionRequired":false,"sourceName":"Fixture","licenseText":"CC0 evidence"},
                "library":{"id":"fixture-pack","name":"Fixture pack","version":"1","source":"fixture source"}
            }]
        }""".trimIndent())

        assertEquals(
            mapOf("closedHat" to 42, "kick" to 36, "openHat" to 46, "snare" to 38),
            InstrumentRegistryLoader(root).load().resolve("fixture-drum-kit").noteMap
        )
    }

    @Test
    fun `approved logical stem binding excludes multi-role instruments assigned to another stem`() {
        copyLibrary()
        val license = """"license":{"id":"CC0-1.0","commercialUse":true,"attributionRequired":false,"sourceName":"Fixture","licenseText":"CC0 evidence"}"""
        val library = """"library":{"id":"fixture-pack","name":"Fixture pack","version":"1","source":"fixture source"}"""
        fun entry(id: String, roles: String, path: String) = """{"id":"$id","name":"$id","roles":$roles,"engine":{"type":"sfz","path":"$path"},$license,$library}"""
        Files.writeString(root.resolve("instruments.json"), """{"version":2,"workingSampleRate":44100,"midiChannelConvention":"one-based","instruments":[${entry("fixture-pad", "[\"texture\"]", "pad/pad.sfz")},${entry("fixture-strings", "[\"counter-melody\",\"texture\"]", "strings/strings.sfz")}] }""")
        val provenance = LibraryProvenanceSnapshot("fixture-pack", "0".repeat(64), "1".repeat(64))
        val project = Project(
            name = "bindings",
            envelope = ProjectV4Envelope(
                structureOccurrences = listOf(StructureOccurrence("A1", "A")),
                arrangementAssignments = listOf(
                    ArrangementAssignmentReference("A1", "fixture-pad", "2".repeat(64), provenance, "pad"),
                    ArrangementAssignmentReference("A1", "fixture-strings", "3".repeat(64), provenance, "strings")
                )
            )
        )

        assertEquals("fixture-pad", InstrumentRegistryLoader(root).load().resolveApprovedRole(project, LogicalInstrument.PAD).id)
    }

    @Test
    fun `rejects path traversal missing license invalid channel and drum mapping`() {
        copyLibrary()
        replace("\"piano/piano.sfz\"", "\"../piano.sfz\"")
        assertTrue(failure().contains("must be relative"))

        copyLibrary(); replace("\"licenseId\":\"starter-generated\"", "\"licenseId\":\"missing\"")
        assertTrue(failure().contains("missing license"))

        copyLibrary(); replace("\"midiChannel\":10", "\"midiChannel\":17")
        assertTrue(failure().contains("one-based 1..16"))

        copyLibrary(); replace("\"kick\":36", "\"kick\":35")
        assertTrue(failure().contains("disagrees"))
    }

    @Test
    fun `rejects duplicate names unknown engines and absolute registry paths`() {
        copyLibrary(); replace("\"bass\":{", "\"piano\":{")
        assertTrue(failure().contains("duplicate logical instrument name 'piano'"))

        copyLibrary(); replace("\"engine\":\"sfz\"", "\"engine\":\"vst\"")
        assertTrue(failure().contains("unsupported engine"))

        copyLibrary(); replace("\"piano/piano.sfz\"", "\"/tmp/piano.sfz\"")
        assertTrue(failure().contains("must be relative"))
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

    @Test
    fun `rejects unsupported WAV encoding channels frame layout and empty audio`() {
        copyLibrary()
        val wave = root.resolve("piano/samples/C2.wav")
        val bytes = Files.readAllBytes(wave); bytes[20] = 2; bytes[21] = 0
        Files.write(wave, bytes)
        assertTrue(failure().contains("PCM or IEEE-float"))

        copyLibrary()
        val noChannels = Files.readAllBytes(wave); noChannels[22] = 0; noChannels[23] = 0
        Files.write(wave, noChannels)
        assertTrue(failure().contains("invalid WAV format"))

        copyLibrary()
        val badLayout = Files.readAllBytes(wave); badLayout[32] = 1; badLayout[33] = 0
        Files.write(wave, badLayout)
        assertTrue(failure().contains("inconsistent PCM frame layout"))

        copyLibrary()
        val emptyData = Files.readAllBytes(wave); emptyData[40] = 0; emptyData[41] = 0; emptyData[42] = 0; emptyData[43] = 0
        Files.write(wave, emptyData.copyOfRange(0, 44))
        assertTrue(failure().contains("no complete frames"))
    }

    private fun failure(): String = assertThrows(IllegalArgumentException::class.java) { InstrumentRegistryLoader(root).load() }.message.orEmpty()
    private fun replace(old: String, new: String) {
        val file = root.resolve("instruments.json")
        Files.writeString(file, Files.readString(file).replace(old, new))
    }
    private fun copyLibrary() {
        val pitched = listOf("piano" to "C2", "bass" to "E1", "pad" to "C3", "strings" to "G3")
        pitched.forEachIndexed { index, (name, sample) ->
            writeWav(root.resolve("$name/samples/$sample.wav"))
            Files.createDirectories(root.resolve(name))
            Files.writeString(root.resolve("$name/$name.sfz"), "<region> sample=samples/$sample.wav key=${36 + index * 12}")
        }
        val drums = listOf("kick" to 36, "snare" to 38, "clap" to 39, "hat_closed" to 42, "hat_open" to 46)
        drums.forEach { (name, _) -> writeWav(root.resolve("drums/samples/$name.wav")) }
        Files.createDirectories(root.resolve("drums"))
        Files.writeString(root.resolve("drums/drums.sfz"), drums.joinToString("\n") { (name, key) -> "<region> sample=samples/$name.wav key=$key" })
        Files.writeString(root.resolve("LICENSES.json"), """{"version":1,"libraries":{"starter-generated":{"displayName":"Fixture","source":"local","provenance":"generated-original","license":"CC0-1.0","commercialUse":true,"attributionRequired":false,"redistribution":"allowed"}}}""")
        Files.writeString(root.resolve("instruments.json"), """{"version":1,"workingSampleRate":44100,"midiChannelConvention":"one-based","instruments":{"piano":{"engine":"sfz","path":"piano/piano.sfz","licenseId":"starter-generated","midiProgram":0},"bass":{"engine":"sfz","path":"bass/bass.sfz","licenseId":"starter-generated","midiProgram":32},"drums":{"engine":"sfz","path":"drums/drums.sfz","licenseId":"starter-generated","midiChannel":10,"noteMap":{"kick":36,"snare":38,"clap":39,"closedHat":42,"openHat":46}},"pad":{"engine":"sfz","path":"pad/pad.sfz","licenseId":"starter-generated","midiProgram":89},"strings":{"engine":"sfz","path":"strings/strings.sfz","licenseId":"starter-generated","midiProgram":48}}}""")
    }

    private fun writeWav(path: Path) {
        Files.createDirectories(requireNotNull(path.parent))
        val data = byteArrayOf(0, 0)
        val bytes = ByteBuffer.allocate(44 + data.size).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray()); bytes.putInt(36 + data.size); bytes.put("WAVEfmt ".toByteArray())
        bytes.putInt(16); bytes.putShort(1); bytes.putShort(1); bytes.putInt(44_100); bytes.putInt(88_200)
        bytes.putShort(2); bytes.putShort(16); bytes.put("data".toByteArray()); bytes.putInt(data.size); bytes.put(data)
        Files.write(path, bytes.array())
    }
}
