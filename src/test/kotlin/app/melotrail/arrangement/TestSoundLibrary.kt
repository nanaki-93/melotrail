package app.melotrail.arrangement

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path

/** Small self-contained v1 fixture for tests that exercise legacy logical stems. */
object TestSoundLibrary {
    private val fixtureRoot = Path.of("build", "test-sound-library").toAbsolutePath().normalize()

    @Synchronized fun root(): Path {
        if (Files.isRegularFile(fixtureRoot.resolve("instruments.json"))) return fixtureRoot
        val pitched = listOf("piano" to "C2", "bass" to "E1", "pad" to "C2", "strings" to "C2")
        pitched.forEachIndexed { index, (name, sample) ->
            writeSample(fixtureRoot.resolve("$name/samples/$sample.wav"))
            Files.createDirectories(fixtureRoot.resolve(name))
            Files.writeString(fixtureRoot.resolve("$name/$name.sfz"), "<region> sample=samples/$sample.wav key=${36 + index * 12}")
        }
        val drums = listOf("kick" to 36, "snare" to 38, "clap" to 39, "hat_closed" to 42, "hat_open" to 46)
        drums.forEach { (name, _) -> writeSample(fixtureRoot.resolve("drums/samples/$name.wav")) }
        Files.createDirectories(fixtureRoot.resolve("drums"))
        Files.writeString(fixtureRoot.resolve("drums/drums.sfz"), drums.joinToString("\n") { (name, key) -> "<region> sample=samples/$name.wav key=$key" })
        Files.writeString(fixtureRoot.resolve("LICENSES.json"), """{"version":1,"libraries":{"fixture":{"displayName":"Fixture Library","source":"fixture-source","provenance":"generated-original","license":"CC0-1.0","commercialUse":true,"attributionRequired":false,"redistribution":"allowed"}}}""")
        Files.writeString(fixtureRoot.resolve("instruments.json"), """{"version":1,"workingSampleRate":44100,"midiChannelConvention":"one-based","instruments":{"piano":{"engine":"sfz","path":"piano/piano.sfz","licenseId":"fixture","midiProgram":0},"bass":{"engine":"sfz","path":"bass/bass.sfz","licenseId":"fixture","midiProgram":32},"drums":{"engine":"sfz","path":"drums/drums.sfz","licenseId":"fixture","midiChannel":10,"noteMap":{"kick":36,"snare":38,"clap":39,"closedHat":42,"openHat":46}},"pad":{"engine":"sfz","path":"pad/pad.sfz","licenseId":"fixture","midiProgram":89},"strings":{"engine":"sfz","path":"strings/strings.sfz","licenseId":"fixture","midiProgram":48}}}""")
        return fixtureRoot
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
