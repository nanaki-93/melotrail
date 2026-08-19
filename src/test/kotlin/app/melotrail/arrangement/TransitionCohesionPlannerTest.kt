package app.melotrail.arrangement

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TransitionCohesionPlannerTest {
    @Test fun `model response is path-free and carries arrangement context identity`() {
        val outgoingHash = "a".repeat(64); val incomingHash = "b".repeat(64)
        val arrangementHash = "e".repeat(64); val contextHash = "f".repeat(64)
        val input = TransitionCohesionInput(inputHash = "c".repeat(64), structureSha256 = "d".repeat(64), arrangementSha256 = arrangementHash, contextSha256 = contextHash, supportedInstruments = listOf("drums"), boundaries = listOf(TransitionBoundaryInput("phrase11", "phrase12", evidence("phrase1", outgoingHash), evidence("phrase1", incomingHash), listOf(TransitionRoleAction.DRUM_FILL), policy(contextHash))))
        val trustedModel = CohesionModelIdentity("qwen", "local", arrangementHash)
        val response = """{"version":3,"inputHash":"${input.inputHash}","arrangementSha256":"$arrangementHash","contextSha256":"$contextHash","boundaries":[{"outgoingInstanceId":"phrase11","incomingInstanceId":"phrase12","outgoingHash":"$outgoingHash","incomingHash":"$incomingHash","arrangementSha256":"$arrangementHash","contextSha256":"$contextHash","roleAction":"DRUM_FILL","bridgeType":"DRUM_FILL","bars":1,"instrument":"drums","harmonicHandoff":"HOLD","rhythmicGesture":"FILL","energyContour":"RISE","tempoHandoff":"PRESERVE","meterHandoff":"PRESERVE","rationale":"Carry energy forward"}]}"""
        val plan = LocalQwenTransitionCohesionPlanner(LocalQwenClient { _, _ -> response }, trustedModel).plan(input)
        assertEquals(trustedModel, plan.model); assertEquals(BridgeType.DRUM_FILL, plan.boundaries.single().bridgeType); assertEquals(outgoingHash, plan.boundaries.single().outgoingHash)
    }

    @Test fun `mixed meter minor flat-key evidence is valid when PPQ matches`() {
        val hash = "a".repeat(64); val arrangement = "b".repeat(64); val context = "c".repeat(64)
        val outgoing = evidence("A", hash, MidiKey("Db", "minor", 0.8), MidiTimeSignature(0, 3, 4))
        val incoming = evidence("B", hash, MidiKey("F#", "minor", 0.8), MidiTimeSignature(0, 6, 8))
        val input = TransitionCohesionInput(inputHash = "d".repeat(64), structureSha256 = "e".repeat(64), arrangementSha256 = arrangement, contextSha256 = context, supportedInstruments = listOf("bass"), boundaries = listOf(TransitionBoundaryInput("A1", "B1", outgoing, incoming, listOf(TransitionRoleAction.BASS_MOTION), policy(context, TransitionRoleAction.BASS_MOTION))))
        val plan = TransitionCohesionPlan(inputHash = input.inputHash, arrangementSha256 = arrangement, contextSha256 = context, model = CohesionModelIdentity.DETERMINISTIC, boundaries = listOf(TransitionBridgePlan("A1", "B1", hash, hash, arrangement, context, TransitionRoleAction.BASS_MOTION, BridgeType.BASS_WALK, 1, "bass", HarmonicHandoff.STEP_TO_INCOMING, RhythmicGesture.PICKUP, EnergyContour.RISE, rationale = "Move into the incoming minor harmony")))
        assertTrue(TransitionCohesionValidator.validate(plan, input).isValid)
    }

    @Test fun `validator rejects malformed action and stale arrangement hash`() {
        val hash = "a".repeat(64); val arrangement = "b".repeat(64); val context = "c".repeat(64)
        val input = TransitionCohesionInput(inputHash = "d".repeat(64), structureSha256 = "e".repeat(64), arrangementSha256 = arrangement, contextSha256 = context, supportedInstruments = listOf("drums"), boundaries = listOf(TransitionBoundaryInput("A1", "B1", evidence("A", hash), evidence("B", hash), listOf(TransitionRoleAction.DRUM_FILL), policy(context))))
        val unsafe = TransitionCohesionPlan(inputHash = input.inputHash, arrangementSha256 = "f".repeat(64), contextSha256 = context, model = CohesionModelIdentity.DETERMINISTIC, boundaries = listOf(TransitionBridgePlan("A1", "B1", hash, hash, "f".repeat(64), context, TransitionRoleAction.BASS_MOTION, BridgeType.BASS_WALK, 3, "drums", HarmonicHandoff.HOLD, RhythmicGesture.FILL, EnergyContour.RISE, rationale = "../unsafe")))
        assertFalse(TransitionCohesionValidator.validate(unsafe, input).isValid)
    }

    private fun policy(hash: String, action: TransitionRoleAction = TransitionRoleAction.DRUM_FILL) = TransitionPolicyEvidence("lofi", "calm", hash, listOf(action))
    private fun evidence(partId: String, sourceHash: String, key: MidiKey? = null, meter: MidiTimeSignature = MidiTimeSignature(0, 4, 4)) = TransitionMusicalEvidence(partId, sourceHash, "f".repeat(64), 480, 1_920, key, emptyList(), MidiTempoChange(0, 80.0), meter, 0.5, TransitionBoundarySummary(true, true, 0, 1_440), TransitionArrangementEvidence("9".repeat(64), SongSectionPurpose.DEVELOPMENT, listOf(TransitionInstrumentEvidence("drums", "DrumsInstrumentPlan", 0.5)), "8".repeat(64)))
}
