package app.melotrail.midi

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class OwnedMidiFixturesTest {
    @TempDir lateinit var temporaryDirectory: Path

    @Test
    fun `owned fixtures have stable bytes hashes headers and declared intent`() {
        assertEquals(10, OwnedMidiFixtures.all.size)
        assertEquals(10, OwnedMidiFixtures.all.map { it.fileName }.toSet().size)

        assertEquals(
            OwnedMidiFixtures.all.associate { it.fileName to it.sha256 },
            OwnedMidiFixtures.all.associate { it.fileName to OwnedMidiFixtures.sha256(it.bytes) },
        )

        OwnedMidiFixtures.all.forEach { fixture ->
            assertTrue(fixture.purpose.isNotBlank(), fixture.fileName)
            assertTrue(fixture.bytes.size <= 256, fixture.fileName)
            if (fixture.expectedFormat != null) {
                val header = ByteBuffer.wrap(fixture.bytes, 0, 14).order(ByteOrder.BIG_ENDIAN)
                assertContentEquals("MThd".encodeToByteArray(), ByteArray(4).also(header::get), fixture.fileName)
                assertEquals(6, header.int, fixture.fileName)
                assertEquals(fixture.expectedFormat, header.short.toInt(), fixture.fileName)
                assertEquals(fixture.expectedTracks, header.short.toInt(), fixture.fileName)
            }
        }
    }

    @Test
    fun `owned fixtures materialize as immutable test inputs`() {
        val paths = OwnedMidiFixtures.writeAll(temporaryDirectory)

        assertEquals(OwnedMidiFixtures.all.map { it.fileName }, paths.map { it.fileName.toString() })
        paths.zip(OwnedMidiFixtures.all).forEach { (path, fixture) ->
            assertContentEquals(fixture.bytes, Files.readAllBytes(path), fixture.fileName)
        }
    }
}
