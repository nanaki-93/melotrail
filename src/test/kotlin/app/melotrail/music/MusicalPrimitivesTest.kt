package app.melotrail.music

import app.melotrail.arrangement.MidiKey
import app.melotrail.arrangement.toMusicalKeyOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MusicalPrimitivesTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun `all supported spellings retain their spelling and enharmonics compare chromatically`() {
        PitchSpelling.entries.forEach { spelling ->
            val pitch = PitchClass.of(spelling)
            assertEquals(spelling, pitch.spelling)
            assertEquals(spelling.chromatic, pitch.chromatic)
        }

        val sharp = PitchClass.of(PitchSpelling.C_SHARP)
        val flat = PitchClass.of(PitchSpelling.D_FLAT)
        assertEquals(sharp, flat)
        assertEquals("C#", sharp.toString())
        assertEquals("Db", flat.toString())
        assertEquals(11, sharp.ascendingIntervalTo(PitchClass.of(PitchSpelling.C)))
    }

    @Test
    fun `major and natural minor provide membership without changing notes`() {
        val cMajor = MusicalKey(PitchClass.of(PitchSpelling.C), ScaleModeId.MAJOR)
        val aMinor = MusicalKey(PitchClass.of(PitchSpelling.A), ScaleModeId.NATURAL_MINOR)

        assertEquals(listOf(0, 2, 4, 5, 7, 9, 11), cMajor.scalePitchClasses().map(PitchClass::chromatic))
        assertTrue(cMajor.contains(PitchClass.of(PitchSpelling.F_SHARP)).not())
        assertEquals(listOf(9, 11, 0, 2, 4, 5, 7), aMinor.scalePitchClasses().map(PitchClass::chromatic))
        assertTrue(aMinor.contains(PitchClass.of(PitchSpelling.C)))
        assertEquals("Eb major", MusicalKey(PitchClass.of(PitchSpelling.E_FLAT), ScaleModeId.MAJOR).displayName)
    }

    @Test
    fun `serialization is deterministic and unknown modes survive without executable behavior`() {
        val key = MusicalKey(PitchClass.of(PitchSpelling.E_FLAT), ScaleModeId("future-mode-v2"))
        val encoded = json.encodeToString(key)

        assertEquals("{\"tonic\":{\"chromatic\":3,\"spelling\":\"Eb\"},\"modeId\":\"future-mode-v2\"}", encoded)
        assertEquals(key, json.decodeFromString<MusicalKey>(encoded))
        assertFalse(key.isExecutable)
        assertFailsWith<IllegalArgumentException> { key.scalePitchClasses() }
    }

    @Test
    fun `tempo and meter reject invalid values while supporting non four four meters`() {
        assertFailsWith<IllegalArgumentException> { Tempo(0.0) }
        assertFailsWith<IllegalArgumentException> { Tempo(Double.NaN) }
        assertFailsWith<IllegalArgumentException> { TimeSignature(0, 4) }
        assertFailsWith<IllegalArgumentException> { TimeSignature(4, 3) }
        assertEquals("7/8", TimeSignature(7, 8).displayName)
        assertEquals(listOf("C", "C#", "D"), MusicalOptionModels.tonics.take(3).map(TonicOption::label))
        assertEquals(listOf("2/4", "3/4", "4/4"), MusicalOptionModels.timeSignatures.take(3).map(TimeSignatureOption::label))
    }

    @Test
    fun `legacy midi key evidence is validated before processors receive it`() {
        assertEquals("Db natural minor", requireNotNull(MidiKey("Db", "minor", 0.8).toMusicalKeyOrNull()).displayName)
        assertNull(MidiKey("H", "major", 0.8).toMusicalKeyOrNull())
        assertNull(MidiKey("C", "dorian", 0.8).toMusicalKeyOrNull())
    }
}
