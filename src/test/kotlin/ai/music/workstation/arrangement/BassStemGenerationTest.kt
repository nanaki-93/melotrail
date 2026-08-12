package ai.music.workstation.arrangement

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path

class BassStemGenerationTest {
    @TempDir
    lateinit var projectRoot: Path

    @Test
    fun `renders lossless bass WAV in stems at actual multichannel sample format and preserves source`() {
        val source = createSource("A")
        val sourceBefore = Files.readString(source)
        val project = Project(name = "demo", parts = listOf(Part("A", "parts/A.wav")))
        val arrangement = bassArrangement("A", "A")
        val analysis = analysis(sampleRate = 32_000, channels = 3, frameCount = 3_200)

        val stem = BassStemGenerationAdapter().generate(
            projectRoot,
            project,
            arrangement,
            mapOf("A" to analysis)
        )

        val bytes = Files.readAllBytes(stem.path)
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(projectRoot.resolve("stems/bass.wav"), stem.path)
        assertEquals(sourceBefore, Files.readString(source))
        assertEquals(32_000, stem.sampleRate)
        assertEquals(3, stem.channels)
        assertEquals(6_400, stem.frameCount)
        assertEquals(listOf(0, 3_200), stem.notes.map { it.startFrame })
        assertEquals(listOf(3_200, 3_200), stem.notes.map { it.durationFrames })
        assertEquals("RIFF", bytes.copyOfRange(0, 4).decodeToString())
        assertEquals(1, header.getShort(20).toInt())
        assertEquals(3, header.getShort(22).toInt())
        assertEquals(32_000, header.getInt(24))
        assertEquals(24, header.getShort(34).toInt())
        assertEquals(6_400 * 3 * 3, header.getInt(40))
        assertTrue(bytes.copyOfRange(44, bytes.size).any { it.toInt() != 0 })
    }

    @Test
    fun `rejects a timeline with mixed formats rather than guessing a stem format`() {
        createSource("A")
        createSource("B")
        val project = Project(
            name = "demo",
            parts = listOf(Part("A", "parts/A.wav"), Part("B", "parts/B.wav"))
        )

        val exception = assertThrows(IllegalArgumentException::class.java) {
            BassStemGenerationAdapter().generate(
                projectRoot,
                project,
                bassArrangement("A", "B"),
                mapOf(
                    "A" to analysis(sampleRate = 44_100, channels = 1, frameCount = 441),
                    "B" to analysis(sampleRate = 48_000, channels = 2, frameCount = 480)
                )
            )
        }

        assertTrue(exception.message.orEmpty().contains("same sample rate and channels"))
        assertFalse(Files.exists(projectRoot.resolve("stems/bass.wav")))
    }

    @Test
    fun `rejects arrangements without a generated bass plan`() {
        createSource("A")
        val project = Project(name = "demo", parts = listOf(Part("A", "parts/A.wav")))
        val arrangement = Arrangement(
            sections = listOf(
                ArrangementSection(
                    index = 0,
                    partId = "A",
                    instruments = listOf(InstrumentPlan("piano", InstrumentMode.SOURCE))
                )
            )
        )

        val exception = assertThrows(IllegalArgumentException::class.java) {
            BassStemGenerationAdapter().generate(projectRoot, project, arrangement, mapOf("A" to analysis()))
        }

        assertTrue(exception.message.orEmpty().contains("does not contain a generated bass"))
    }

    private fun bassArrangement(vararg partIds: String): Arrangement = Arrangement(
        sections = partIds.mapIndexed { index, partId ->
            ArrangementSection(
                index = index,
                partId = partId,
                instruments = listOf(
                    InstrumentPlan("piano", InstrumentMode.SOURCE),
                    InstrumentPlan("bass", InstrumentMode.GENERATED, "root_fifth", 0.5)
                )
            )
        }
    )

    private fun analysis(
        sampleRate: Int = 44_100,
        channels: Int = 1,
        frameCount: Long = 441
    ) = PartAnalysis(
        duration = frameCount.toDouble() / sampleRate,
        sampleRate = sampleRate,
        channels = channels,
        frameCount = frameCount,
        peak = 0.5,
        rms = 0.25,
        nearSilence = false
    )

    private fun createSource(id: String): Path {
        val source = projectRoot.resolve("parts/$id.wav")
        Files.createDirectories(source.parent)
        Files.writeString(source, "source-$id")
        return source
    }
}
