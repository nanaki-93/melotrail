package app.melotrail.application

import app.melotrail.arrangement.BridgeType
import app.melotrail.arrangement.EnsembleCohesionModelIdentity
import app.melotrail.arrangement.EnergyContour
import app.melotrail.arrangement.HarmonicHandoff
import app.melotrail.arrangement.RhythmicGesture
import app.melotrail.arrangement.TimingHandoff
import app.melotrail.arrangement.TransitionRoleAction
import app.melotrail.arrangement.TransitionBridgePlan
import app.melotrail.arrangement.EnsembleCohesionPlan
import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.WorkflowArtifact
import java.nio.file.Path

/** Record the explicit source-song gate decision required before test arrangements. */
fun approveSourceSongForArrangement(root: Path) {
    val critic = DefaultSourceSongCriticApplicationService()
    val report = critic.run(root)
    if (report.report.hasBlockingIssues) {
        critic.approve(root, overrideBlockingIssues = true, overrideReason = "Fixture keeps its authored source melody.")
    } else {
        critic.approve(root)
    }
}

private data class CohesionTestChoice(
    val action: TransitionRoleAction,
    val type: BridgeType,
    val instrument: String,
    val gesture: RhythmicGesture
)

/** Offline test-only local-model fake; production Cohesion never substitutes this plan. */
suspend fun generateApprovedCohesion(root: Path, arrangements: ArrangementApplicationService = DefaultArrangementApplicationService(libraryRoot = root)) {
    val service = DefaultEnsembleCohesionApplicationService(ensemblePreparation = EnsembleMidiPreparation { projectRoot, progress ->
        val workflow = ProjectStore.read(projectRoot).workflow
        if (workflow.generatedMidi == null || WorkflowArtifact.GENERATED_MIDI in workflow.stale) {
            arrangements.generateRequiredMidi(projectRoot, progress)
        }
    }) { input ->
        EnsembleCohesionPlan(inputHash = input.inputHash, arrangementSha256 = input.arrangementSha256, contextSha256 = input.contextSha256, model = EnsembleCohesionModelIdentity("qwen", "test", "1".repeat(64)), boundaries = input.boundaries.map { boundary ->
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
    val draft = service.generate(GenerateEnsembleCohesionRequest(root))
    service.approve(root)
}
