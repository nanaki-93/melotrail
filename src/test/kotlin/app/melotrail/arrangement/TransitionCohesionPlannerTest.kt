package app.melotrail.arrangement

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TransitionCohesionPlannerTest {
    @Test
    fun `model response uses the concrete bridge schema without model provenance`() {
        val outgoingHash = "a".repeat(64)
        val incomingHash = "b".repeat(64)
        val input = TransitionCohesionInput(
            inputHash = "c".repeat(64),
            structureSha256 = "d".repeat(64),
            arrangementSha256 = "e".repeat(64),
            supportedInstruments = listOf("bass", "drums", "pad"),
            boundaries = listOf(
                TransitionBoundaryInput(
                    outgoingInstanceId = "phrase11",
                    incomingInstanceId = "phrase12",
                    outgoing = evidence("phrase1", outgoingHash),
                    incoming = evidence("phrase1", incomingHash)
                )
            )
        )
        val trustedModel = CohesionModelIdentity("qwen", "local", "e".repeat(64))
        val response = """{"version":2,"inputHash":"${input.inputHash}","boundaries":[{"outgoingInstanceId":"phrase11","incomingInstanceId":"phrase12","outgoingHash":"$outgoingHash","incomingHash":"$incomingHash","bridgeType":"DRUM_FILL","bars":1,"instrument":"drums","harmonicHandoff":"HOLD","rhythmicGesture":"FILL","energyContour":"RISE","tempoHandoff":"PRESERVE","meterHandoff":"PRESERVE","rationale":"Carry energy forward"}]}"""

        val plan = LocalQwenTransitionCohesionPlanner(LocalQwenClient { _, _ -> response }, trustedModel).plan(input)

        assertEquals(trustedModel, plan.model)
        assertEquals(BridgeType.DRUM_FILL, plan.boundaries.single().bridgeType)
        assertEquals(outgoingHash, plan.boundaries.single().outgoingHash)
    }

    private fun evidence(partId: String, sourceHash: String) = TransitionMusicalEvidence(
        partId = partId,
        sourceHash = sourceHash,
        analysisHash = "f".repeat(64),
        ppq = 480,
        durationTicks = 1_920,
        key = null,
        chords = emptyList(),
        tempo = MidiTempoChange(0, 80.0),
        meter = MidiTimeSignature(0, 4, 4),
        energy = 0.5,
        boundary = MelodyBoundarySummary(true, true, 0, 1_440)
    )
}
