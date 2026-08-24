package app.melotrail.arrangement

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class EnsembleCohesionPlannerTest {
    @TempDir lateinit var root: Path

    @Test fun `model receives only adjacent boundary evidence and binds it to the current input`() {
        val input = input("phrase11" to "phrase12")
        val trustedModel = EnsembleCohesionModelIdentity("qwen", "local", "e".repeat(64))
        val response = """{"boundaries":[{"roleAction":"DRUM_FILL","bars":1,"harmonicHandoff":"HOLD","rhythmicGesture":"FILL","energyContour":"RISE","rationale":"Carry energy forward"}]}"""
        var prompt = ""

        val plan = LocalQwenEnsembleCohesionPlanner(LocalQwenClient { _, userPrompt -> prompt = userPrompt; response }, trustedModel).plan(input)

        assertEquals(trustedModel, plan.model)
        assertEquals(listOf("phrase11" to "phrase12"), plan.boundaries.map { it.outgoingInstanceId to it.incomingInstanceId })
        assertFalse(prompt.contains(input.inputHash))
        assertFalse(prompt.contains(input.arrangementSha256))
        assertFalse(prompt.contains("phrase11"))
    }

    @Test fun `two and repeated occurrences require the exact adjacent boundary sequence`() {
        val repeated = input("A1" to "A2", "A2" to "A3")
        val exact = plan(repeated)
        assertTrue(EnsembleCohesionValidator.validate(exact, repeated).isValid)

        assertFalse(EnsembleCohesionValidator.validate(exact.copy(boundaries = exact.boundaries.dropLast(1)), repeated).isValid)
        assertFalse(EnsembleCohesionValidator.validate(exact.copy(boundaries = exact.boundaries.reversed()), repeated).isValid)
        val two = input("A1" to "A2")
        assertTrue(EnsembleCohesionValidator.validate(plan(two), two).isValid)
    }

    @Test fun `Qwen cohesion retries an incomplete boundary response with its validation error`() {
        val input = input("A1" to "A2", "A2" to "A3")
        val prompts = mutableListOf<String>()
        var calls = 0
        val incomplete = response(1)
        val complete = response(2)
        val planner = LocalQwenEnsembleCohesionPlanner(LocalQwenClient { _, prompt ->
            prompts += prompt
            if (calls++ == 0) incomplete else complete
        }, EnsembleCohesionModelIdentity.DETERMINISTIC)

        val plan = planner.plan(input)

        assertEquals(2, calls)
        assertEquals(2, plan.boundaries.size)
        assertTrue(prompts[1].contains("Automatic repair attempt 1 of 5"))
        assertTrue(prompts[1].contains("Qwen returned 1 cohesion decisions for 2 boundaries"))
    }

    @Test fun `Qwen cohesion bounds display rationale without changing musical decisions`() {
        val input = input("A1" to "A2")
        val response = """{"boundaries":[{"roleAction":"DRUM_FILL","bars":1,"harmonicHandoff":"HOLD","rhythmicGesture":"FILL","energyContour":"RISE","rationale":"${"Carry the groove forward! ".repeat(12)}"}]}"""

        val plan = LocalQwenEnsembleCohesionPlanner(LocalQwenClient { _, _ -> response }, EnsembleCohesionModelIdentity.DETERMINISTIC).plan(input)

        assertTrue(plan.boundaries.single().rationale.matches(Regex("[A-Za-z0-9 ,.'-]{1,180}")))
        assertEquals(TransitionRoleAction.DRUM_FILL, plan.boundaries.single().roleAction)
    }

    @Test fun `boundary edits accept first and last ticks but reject one tick outside either window`() {
        val input = input("A1" to "A2", notes =
            List(20) { index -> CohesionMelodyNote(if (index == 0) "outgoing-last" else "outgoing-$index", 0, 60, 72, 3_839, 3_840) } +
                List(20) { index -> CohesionMelodyNote(if (index == 0) "incoming-first" else "incoming-$index", 0, 62, 72, 0, 1) }
        )
        val bridge = plan(input).boundaries.single()
        val edgePlan = plan(input, bridge.copy(melodyEdits = listOf(
            CohesionMelodyEdit("A1", CohesionMelodyEditKind.SET_VELOCITY, "outgoing-last", value = 80, reason = "shape boundary arrival"),
            CohesionMelodyEdit("A2", CohesionMelodyEditKind.SET_VELOCITY, "incoming-first", value = 80, reason = "shape boundary departure")
        )))
        assertTrue(EnsembleCohesionValidator.validate(edgePlan, input).isValid)

        val outgoingOutside = edgePlan.copy(boundaries = listOf(bridge.copy(melodyEdits = listOf(
            CohesionMelodyEdit("A1", CohesionMelodyEditKind.ADD_NOTE, "add-00000", pitch = 60, velocity = 72, startTick = 1_919, durationTicks = 1, channel = 0, anchorNoteId = "outgoing-last", reason = "outside outgoing boundary")
        ))))
        val incomingOutside = edgePlan.copy(boundaries = listOf(bridge.copy(melodyEdits = listOf(
            CohesionMelodyEdit("A2", CohesionMelodyEditKind.ADD_NOTE, "add-00000", pitch = 62, velocity = 72, startTick = 1_920, durationTicks = 1, channel = 0, anchorNoteId = "incoming-first", reason = "outside incoming boundary")
        ))))
        assertFalse(EnsembleCohesionValidator.validate(outgoingOutside, input).isValid)
        assertFalse(EnsembleCohesionValidator.validate(incomingOutside, input).isValid)
    }

    @Test fun `superseded whole song payloads are rejected before publication`() {
        val input = input("A1" to "A2")
        val superseded = Json.encodeToString(EnsembleCohesionPlan.serializer(), plan(input)).dropLast(1) + ",\"songEdits\":[]}"
        val draft = root.resolve(EnsembleCohesionStore.DRAFT_FILE)
        Files.createDirectories(draft.parent)
        Files.writeString(draft, superseded)

        assertThrows(Exception::class.java) { EnsembleCohesionStore.readDraft(root, input) }
        assertFalse(Files.exists(root.resolve(EnsembleCohesionStore.bridgeMidi("A1", "A2"))))
    }

    @Test fun `model whole song edit JSON is rejected by the boundary-only schema`() {
        val input = input("A1" to "A2")
        val response = """{"boundaries":[],"songEdits":[]}"""

        assertThrows(IllegalArgumentException::class.java) {
            LocalQwenEnsembleCohesionPlanner(LocalQwenClient { _, _ -> response }, EnsembleCohesionModelIdentity.DETERMINISTIC).plan(input)
        }
    }

    @Test fun `stale boundary hashes fail before any derived artifact is written`() {
        val input = input("A1" to "A2")
        val stale = plan(input).copy(boundaries = plan(input).boundaries.map { it.copy(outgoingHash = "f".repeat(64)) })

        assertThrows(IllegalArgumentException::class.java) { EnsembleCohesionStore.writeDraft(root, input, stale) }
        assertFalse(Files.exists(root.resolve(EnsembleCohesionStore.DRAFT_FILE)))
        assertFalse(Files.exists(root.resolve(EnsembleCohesionStore.bridgeMidi("A1", "A2"))))
    }

    private fun input(vararg boundaries: Pair<String, String>, notes: List<CohesionMelodyNote> = emptyList()): EnsembleCohesionInput {
        val hash = "a".repeat(64); val context = "c".repeat(64)
        val occurrenceEvidence = boundaries.flatMap { listOf(it.first, it.second) }.distinct().associateWith { id ->
            evidence(id, hash, notes.filter { note -> note.id.startsWith(if (id.endsWith("1")) "outgoing" else "incoming") })
        }
        return EnsembleCohesionInput(
            inputHash = "d".repeat(64), structureSha256 = "b".repeat(64), arrangementSha256 = "e".repeat(64), contextSha256 = context,
            supportedInstruments = listOf("drums"),
            boundaries = boundaries.map { (outgoing, incoming) ->
                TransitionContext(outgoing, incoming, occurrenceEvidence.getValue(outgoing), occurrenceEvidence.getValue(incoming), listOf(TransitionRoleAction.DRUM_FILL), policy(context))
            }
        )
    }

    private fun plan(input: EnsembleCohesionInput, override: TransitionBridgePlan? = null) = EnsembleCohesionPlan(
        inputHash = input.inputHash, arrangementSha256 = input.arrangementSha256, contextSha256 = input.contextSha256,
        model = EnsembleCohesionModelIdentity.DETERMINISTIC,
        boundaries = input.boundaries.map { boundary -> override ?: TransitionBridgePlan(
            boundary.outgoingInstanceId, boundary.incomingInstanceId, boundary.outgoing.sourceHash, boundary.incoming.sourceHash,
            input.arrangementSha256, input.contextSha256, TransitionRoleAction.DRUM_FILL, BridgeType.DRUM_FILL, 1, "drums",
            HarmonicHandoff.HOLD, RhythmicGesture.FILL, EnergyContour.RISE, rationale = "Carry energy forward"
        ) }
    )

    private fun response(boundaries: Int): String = """{"boundaries":[${List(boundaries) {
        """{"roleAction":"DRUM_FILL","bars":1,"harmonicHandoff":"HOLD","rhythmicGesture":"FILL","energyContour":"RISE","rationale":"Carry energy forward"}"""
    }.joinToString(",")}]}"""

    private fun policy(hash: String) = TransitionPolicyEvidence("lofi", "calm", hash, listOf(TransitionRoleAction.DRUM_FILL))
    private fun evidence(partId: String, sourceHash: String, notes: List<CohesionMelodyNote>) = TransitionMusicalEvidence(
        partId, sourceHash, "f".repeat(64), 480, 3_840, MidiKey("C", "major", 1.0), emptyList(), MidiTempoChange(0, 80.0), MidiTimeSignature(0, 4, 4), 0.5,
        TransitionBoundarySummary(true, true, 0, 1_440), TransitionArrangementEvidence("9".repeat(64), SongSectionPurpose.DEVELOPMENT, listOf(TransitionInstrumentEvidence("drums", "DrumsInstrumentPlan", 0.5)), "8".repeat(64)), notes
    )
}
