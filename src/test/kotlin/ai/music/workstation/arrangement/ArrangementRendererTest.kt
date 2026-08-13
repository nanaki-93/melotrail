package ai.music.workstation.arrangement

import ai.music.workstation.audio.AudioBuffer
import ai.music.workstation.audio.AudioFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ArrangementRendererTest {
    @Test
    fun `renders a bounded local bridge and generated stems on one timeline`() {
        val format = AudioFormat(8_000, 1, 24, false, false, "WAV")
        val source = AudioBuffer(FloatArray(8_000) { 0.05f }, format, 1.0)
        val arrangement = Arrangement(
            version = 2,
            sections = listOf(
                ArrangementSection(
                    0, "A",
                    listOf(InstrumentPlan("source", InstrumentMode.SOURCE), InstrumentPlan("bass", InstrumentMode.GENERATED, density = 0.5)),
                    TransitionPlan(TransitionType.BRIDGE, bars = 1, bridge = BridgePlan(0.6, listOf(BridgeElement.DRUM_FILL, BridgeElement.PAD_SWELL)))
                ),
                ArrangementSection(1, "B", listOf(InstrumentPlan("source", InstrumentMode.SOURCE), InstrumentPlan("drums", InstrumentMode.GENERATED, density = 0.5)))
            )
        )

        val rendered = ArrangementRenderer().render(arrangement, listOf(source, source), mapOf("A" to analysis(120.0)))

        assertEquals(1, rendered.boundaries.size)
        assertEquals(8_000 * 2, rendered.boundaries.single().endFrame - rendered.boundaries.single().startFrame)
        assertTrue(rendered.tracks.map { it.name }.containsAll(listOf("bass", "drums", "bridges")))
        assertTrue(rendered.frameCount > source.length * 2)
    }

    private fun analysis(bpm: Double) = PartAnalysis(1.0, 8_000, 1, 8_000, 0.1, 0.02, false, bpm = bpm)
}
