package app.melotrail.application

import app.melotrail.arrangement.BridgeType
import app.melotrail.arrangement.CohesionModelIdentity
import app.melotrail.arrangement.EnergyContour
import app.melotrail.arrangement.HarmonicHandoff
import app.melotrail.arrangement.RhythmicGesture
import app.melotrail.arrangement.TimingHandoff
import app.melotrail.arrangement.TransitionRoleAction
import app.melotrail.arrangement.TransitionBridgePlan
import app.melotrail.arrangement.TransitionCohesionPlan
import java.nio.file.Path

private data class CohesionTestChoice(
    val action: TransitionRoleAction,
    val type: BridgeType,
    val instrument: String,
    val gesture: RhythmicGesture
)

/** Offline test-only local-model fake; production Cohesion never substitutes this plan. */
suspend fun generateApprovedCohesion(root: Path, arrangements: ArrangementApplicationService = DefaultArrangementApplicationService(libraryRoot = root)) {
    val service = DefaultCohesionApplicationService(ensemblePreparation = EnsembleMidiPreparation { projectRoot, progress ->
        arrangements.generateRequiredMidi(projectRoot, progress)
    }) { input ->
        TransitionCohesionPlan(inputHash = input.inputHash, arrangementSha256 = input.arrangementSha256, contextSha256 = input.contextSha256, model = CohesionModelIdentity("qwen", "test", "1".repeat(64)), boundaries = input.boundaries.map { boundary ->
            val choice = listOf(
                CohesionTestChoice(TransitionRoleAction.DRUM_FILL, BridgeType.DRUM_FILL, "drums", RhythmicGesture.FILL),
                CohesionTestChoice(TransitionRoleAction.BASS_MOTION, BridgeType.BASS_WALK, "bass", RhythmicGesture.PICKUP),
                CohesionTestChoice(TransitionRoleAction.SUSTAINED_TEXTURE, BridgeType.PAD_SUSTAIN, "pad", RhythmicGesture.SUSTAIN),
                CohesionTestChoice(TransitionRoleAction.SUSTAINED_TEXTURE, BridgeType.PAD_SUSTAIN, "strings", RhythmicGesture.SUSTAIN),
                CohesionTestChoice(TransitionRoleAction.CHORD_MOTION, BridgeType.CHORD_MOTION, "pad", RhythmicGesture.PICKUP),
                CohesionTestChoice(TransitionRoleAction.CHORD_MOTION, BridgeType.CHORD_MOTION, "strings", RhythmicGesture.PICKUP),
                CohesionTestChoice(TransitionRoleAction.CONTINUITY, BridgeType.CONTINUITY, "drums", RhythmicGesture.SUSTAIN),
                CohesionTestChoice(TransitionRoleAction.CONTINUITY, BridgeType.CONTINUITY, "bass", RhythmicGesture.SUSTAIN),
                CohesionTestChoice(TransitionRoleAction.CONTINUITY, BridgeType.CONTINUITY, "pad", RhythmicGesture.SUSTAIN),
                CohesionTestChoice(TransitionRoleAction.CONTINUITY, BridgeType.CONTINUITY, "strings", RhythmicGesture.SUSTAIN)
            ).first { it.action in boundary.allowedRoleActions && it.instrument in input.supportedInstruments }
            TransitionBridgePlan(boundary.outgoingInstanceId, boundary.incomingInstanceId, boundary.outgoing.sourceHash, boundary.incoming.sourceHash, input.arrangementSha256, input.contextSha256, choice.action,
                choice.type, 1, choice.instrument, HarmonicHandoff.HOLD, choice.gesture, EnergyContour.RISE,
                TimingHandoff.PRESERVE, TimingHandoff.PRESERVE, "Carry energy into the next section")
        })
    }
    val draft = service.generate(GenerateCohesionRequest(root))
    service.approve(root)
}
