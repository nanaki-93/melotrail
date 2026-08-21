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

/** Offline test-only local-model fake; production Cohesion never substitutes this plan. */
suspend fun generateApprovedCohesion(root: Path, arrangements: ArrangementApplicationService = DefaultArrangementApplicationService(libraryRoot = root)) {
    val service = DefaultCohesionApplicationService(ensemblePreparation = EnsembleMidiPreparation { projectRoot, progress ->
        arrangements.generateRequiredMidi(projectRoot, progress)
    }) { input ->
        TransitionCohesionPlan(inputHash = input.inputHash, arrangementSha256 = input.arrangementSha256, contextSha256 = input.contextSha256, model = CohesionModelIdentity("qwen", "test", "1".repeat(64)), boundaries = input.boundaries.map { boundary ->
            TransitionBridgePlan(boundary.outgoingInstanceId, boundary.incomingInstanceId, boundary.outgoing.sourceHash, boundary.incoming.sourceHash, input.arrangementSha256, input.contextSha256, TransitionRoleAction.DRUM_FILL,
                BridgeType.DRUM_FILL, 1, "drums", HarmonicHandoff.HOLD, RhythmicGesture.FILL, EnergyContour.RISE,
                TimingHandoff.PRESERVE, TimingHandoff.PRESERVE, "Carry energy into the next section")
        })
    }
    val draft = service.generate(GenerateCohesionRequest(root))
    service.approve(root)
}
