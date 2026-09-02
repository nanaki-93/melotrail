package app.melotrail.midi

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Small, hand-authored Standard MIDI fixtures owned by the MIDI Core test suite.
 *
 * The fixtures deliberately use raw SMF bytes instead of a production writer so
 * parser tests cannot accidentally validate their own output path.
 */
internal object OwnedMidiFixtures {
    private const val PPQ = 480

    enum class Kind { VALID, MALFORMED }

    data class Fixture(
        val fileName: String,
        val purpose: String,
        val kind: Kind,
        val expectedFormat: Int?,
        val expectedTracks: Int?,
        val bytes: ByteArray,
        val sha256: String,
    )

    val all: List<Fixture> = listOf(
        fixture(
            "smf0-melody.mid",
            "SMF 0, one PPQ melody track with tempo, meter, and a normal note-off.",
            Kind.VALID,
            0,
            1,
            midi(0, PPQ, track(trackName("Melody"), tempo(), meter(), noteOn(0, 60, 96), noteOff(480, 60), end())),
            "a2e32b1df5e78867193191a15c82caaa0b7c070b2e328c56b41a1ea5aaba4a35",
        ),
        fixture(
            "smf1-reference-tracks.mid",
            "SMF 1 conductor, melody, and immutable reference-track facts.",
            Kind.VALID,
            1,
            3,
            midi(
                1,
                PPQ,
                track(trackName("Conductor"), tempo(), meter(), end()),
                track(trackName("Melody"), noteOn(0, 64, 100), noteOff(480, 64), end()),
                track(trackName("Reference"), noteOn(0, 48, 80, channel = 1), noteOff(480, 48, channel = 1), end()),
            ),
            "f3166580ebc70d96168ad238471762d20882d86d967a02389b69a96b4c52af67",
        ),
        fixture(
            "whole-song-one-bar.mid",
            "SMF 1 conductor plus exactly one single-channel melody track spanning one complete 4/4 bar.",
            Kind.VALID,
            1,
            2,
            midi(
                1,
                PPQ,
                track(trackName("Conductor"), tempo(), meter(), end()),
                track(trackName("Melody"), noteOn(0, 60, 96), noteOff(960, 60), noteOn(0, 64, 96), noteOff(960, 64), end()),
            ),
            "ca4f370f54bd15ac39e6b8ee314ef98caaff81b48c1cf28de4540b00e92ed4fd",
        ),
        fixture(
            "whole-song-two-bars.mid",
            "SMF 1 conductor plus exactly one single-channel melody track spanning two complete 4/4 bars.",
            Kind.VALID,
            1,
            2,
            midi(
                1,
                PPQ,
                track(trackName("Conductor"), tempo(), meter(), end()),
                track(
                    trackName("Melody"),
                    noteOn(0, 60, 96), noteOff(960, 60), noteOn(0, 64, 96), noteOff(960, 64),
                    noteOn(0, 67, 96), noteOff(960, 67), noteOn(0, 72, 96), noteOff(960, 72), end(),
                ),
            ),
            "eec423c136cc30ed6c082a06a9a2f4a8d9fde1f45030aa878b16086e4eabc69c",
        ),
        fixture(
            "whole-song-three-bars.mid",
            "SMF 1 conductor plus exactly one single-channel melody track spanning three complete 4/4 bars.",
            Kind.VALID,
            1,
            2,
            midi(
                1,
                PPQ,
                track(trackName("Conductor"), tempo(), meter(), end()),
                track(
                    trackName("Melody"),
                    noteOn(0, 60, 96), noteOff(960, 60), noteOn(0, 64, 96), noteOff(960, 64),
                    noteOn(0, 67, 96), noteOff(960, 67), noteOn(0, 72, 96), noteOff(960, 72),
                    noteOn(0, 69, 96), noteOff(960, 69), noteOn(0, 67, 96), noteOff(960, 67), end(),
                ),
            ),
            "287e5db185ca52d1b96cc336001b4075ac536b5b3809685788adeebdc59fa4a4",
        ),
        fixture(
            "pickup-timing.mid",
            "SMF 1 melody whose initial short note is a pickup before the first full-bar phrase.",
            Kind.VALID,
            1,
            2,
            midi(
                1,
                PPQ,
                track(trackName("Conductor"), tempo(), meter(), end()),
                track(trackName("Pickup Melody"), marker(0, "Pickup"), noteOn(0, 67, 90), noteOff(120, 67), noteOn(360, 72, 96), noteOff(480, 72), end()),
            ),
            "edea690670c84305fe8d5ba17e13b3fa3567921faaafb41094dfe1b32242cb7f",
        ),
        fixture(
            "sub-bar-harmony.mid",
            "SMF 1 with deterministic markers at beat zero and the half-bar tick for harmony-window tests.",
            Kind.VALID,
            1,
            2,
            midi(
                1,
                PPQ,
                track(trackName("Conductor"), tempo(), meter(), marker(0, "1:Intro-C"), marker(240, "1:Intro-G7"), end()),
                track(trackName("Melody"), noteOn(0, 60, 96), noteOff(240, 60), noteOn(0, 62, 96), noteOff(240, 62), end()),
            ),
            "507dd7d2f2b57d86b2c95b2019d7b5daf649d63fcb2a13861c5f58bd8ab1dd88",
        ),
        fixture(
            "expressive-controller-pitch.mid",
            "SMF 1 sustained melody with CC64 and pitch bend for supported-expression policy tests.",
            Kind.VALID,
            1,
            2,
            midi(
                1,
                PPQ,
                track(trackName("Conductor"), tempo(), meter(), end()),
                track(trackName("Melody"), controlChange(0, 64, 127), noteOn(0, 69, 88), pitchBend(120, 0, 72), controlChange(360, 64, 0), noteOff(0, 69), end()),
            ),
            "9c08782d6e56327ea64b5ed6aebaf2158b41cea32e95ec6fcb52f79238580ef5",
        ),
        fixture(
            "velocity-zero-note-off.mid",
            "SMF 0 where note-on velocity zero terminates the selected-melody note.",
            Kind.VALID,
            0,
            1,
            midi(0, PPQ, track(trackName("Melody"), tempo(), meter(), noteOn(0, 60, 100), noteOn(480, 60, 0), end())),
            "3006621283cf65a5446dfa4c48b919e71445b830861e77e83dd81f33e2d98bae",
        ),
        fixture(
            "final-boundary-note.mid",
            "SMF 0 with a note ending exactly at the one-bar song boundary.",
            Kind.VALID,
            0,
            1,
            midi(0, PPQ, track(trackName("Melody"), tempo(), meter(), noteOn(1440, 72, 90), noteOff(480, 72), end())),
            "08dde8e7da1e32fbbe6bbfa71937fc2c95e3fa778928a13479e323f163e66044",
        ),
        fixture(
            "truncated-header.mid",
            "Bounded malformed input with an incomplete SMF header.",
            Kind.MALFORMED,
            null,
            null,
            bytes(0x4D, 0x54, 0x68, 0x64, 0x00, 0x00, 0x00),
            "7ed5302ab537819c49fb41c3670d2080240a3c05af841b51bb04ced49d11f4a1",
        ),
        fixture(
            "format-2.mid",
            "Bounded, structurally valid SMF format 2 that V1 must reject.",
            Kind.MALFORMED,
            2,
            1,
            midi(2, PPQ, track(end())),
            "fd8d72b9fa38e47ec870001b8db2828ac60c0874e947e330f2d4c844cf933c5b",
        ),
        fixture(
            "smpte-division.mid",
            "Bounded SMF whose SMPTE division is structurally readable but unsupported in V1.",
            Kind.MALFORMED,
            0,
            1,
            midi(0, 0xE728, track(end())),
            "d18577cd143c20baf9b75f2b36a5369a426c6e8c7ba02074fc26b495fe9646cc",
        ),
    )

    fun writeAll(root: Path): List<Path> = all.map { fixture ->
        root.resolve(fixture.fileName).also { path ->
            Files.createDirectories(requireNotNull(path.parent))
            Files.write(path, fixture.bytes)
        }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private fun fixture(
        fileName: String,
        purpose: String,
        kind: Kind,
        expectedFormat: Int?,
        expectedTracks: Int?,
        bytes: ByteArray,
        sha256: String,
    ) = Fixture(fileName, purpose, kind, expectedFormat, expectedTracks, bytes, sha256)

    private fun midi(format: Int, division: Int, vararg tracks: ByteArray): ByteArray =
        "MThd".encodeToByteArray() + bytes(0, 0, 0, 6, format ushr 8, format, tracks.size ushr 8, tracks.size, division ushr 8, division) + tracks.fold(byteArrayOf()) { all, track -> all + track }

    private fun track(vararg events: ByteArray): ByteArray = chunk("MTrk", events.fold(byteArrayOf()) { all, event -> all + event })

    private fun chunk(name: String, payload: ByteArray): ByteArray =
        name.encodeToByteArray() + bytes(payload.size ushr 24, payload.size ushr 16, payload.size ushr 8, payload.size) + payload

    private fun tempo(): ByteArray = event(0, 0xFF, 0x51, 0x03, 0x07, 0xA1, 0x20)
    private fun meter(): ByteArray = event(0, 0xFF, 0x58, 0x04, 0x04, 0x02, 0x18, 0x08)
    private fun trackName(value: String): ByteArray = text(0, 0x03, value)
    private fun marker(delta: Int, value: String): ByteArray = text(delta, 0x06, value)
    private fun text(delta: Int, type: Int, value: String): ByteArray = event(delta, 0xFF, type, value.length) + value.encodeToByteArray()
    private fun noteOn(delta: Int, pitch: Int, velocity: Int, channel: Int = 0): ByteArray = event(delta, 0x90 or channel, pitch, velocity)
    private fun noteOff(delta: Int, pitch: Int, channel: Int = 0): ByteArray = event(delta, 0x80 or channel, pitch, 0)
    private fun controlChange(delta: Int, controller: Int, value: Int): ByteArray = event(delta, 0xB0, controller, value)
    private fun pitchBend(delta: Int, leastSignificant: Int, mostSignificant: Int): ByteArray = event(delta, 0xE0, leastSignificant, mostSignificant)
    private fun end(): ByteArray = event(0, 0xFF, 0x2F, 0x00)
    private fun event(delta: Int, vararg message: Int): ByteArray = vlq(delta) + bytes(*message)

    private fun vlq(value: Int): ByteArray {
        require(value >= 0)
        val groups = mutableListOf<Int>()
        var remaining = value
        do {
            groups += remaining and 0x7F
            remaining = remaining ushr 7
        } while (remaining > 0)
        return groups.asReversed().mapIndexed { index, group ->
            (if (index == groups.lastIndex) group else group or 0x80).toByte()
        }.toByteArray()
    }

    private fun bytes(vararg values: Int): ByteArray = values.map(Int::toByte).toByteArray()
}
