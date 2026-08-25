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
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage

class EnsembleCohesionPlannerTest {
    @TempDir lateinit var root: Path

    @Test fun `model receives only adjacent boundary evidence and binds it to the current input`() {
        val input = input("phrase11" to "phrase12")
        val trustedModel = EnsembleCohesionModelIdentity("qwen", "local", "e".repeat(64))
        val response = """{"boundaries":[{"roleAction":"DRUM_FILL","harmonicHandoff":"HOLD","rhythmicGesture":"FILL","energyContour":"RISE","rationale":"Carry energy forward"}]}"""
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

    @Test fun `a bridge cannot select a role inactive on both sides of its boundary`() {
        val input = input("A1" to "A2").copy(supportedInstruments = listOf("bass", "drums"))
        val invalid = plan(input).copy(boundaries = plan(input).boundaries.map { bridge ->
            bridge.copy(roleAction = TransitionRoleAction.BASS_MOTION, bridgeType = BridgeType.BASS_WALK, instrument = "bass")
        })

        assertFalse(EnsembleCohesionValidator.validate(invalid, input).isValid)
    }

    @Test fun `continuity is a reviewed no-op rather than a hidden drum fill`() {
        val base = input("A1" to "A2")
        val input = base.copy(boundaries = base.boundaries.map { boundary ->
            boundary.copy(
                allowedRoleActions = listOf(TransitionRoleAction.CONTINUITY),
                transitionPolicy = boundary.transitionPolicy.copy(allowedActions = listOf(TransitionRoleAction.CONTINUITY))
            )
        })
        val plan = DeterministicContinuityEnsembleCohesionPlanner().plan(input)
        val bridge = plan.boundaries.single()
        val path = root.resolve("continuity.mid")
        DeterministicTransitionBridgeEngine.write(path, input, input.boundaries.single(), bridge)
        val noteOns = MidiSystem.getSequence(path.toFile()).tracks.sumOf { track ->
            (0 until track.size()).count { index -> (track[index].message as? javax.sound.midi.ShortMessage)?.let { it.command == javax.sound.midi.ShortMessage.NOTE_ON && it.data2 > 0 } == true }
        }

        assertEquals(TransitionPlacement.NO_OP, bridge.placement)
        assertEquals(0, bridge.leadBeats)
        assertEquals(0, noteOns)
        assertEquals(EnsembleCohesionModelIdentity.DETERMINISTIC, plan.model)
    }

    @Test fun `the saved five boundary fixture remains locally bound in structure order`() {
        val input = input("intro" to "verse", "verse" to "chorus", "chorus" to "breakdown", "breakdown" to "chorus2", "chorus2" to "outro")
        val plan = LocalQwenEnsembleCohesionPlanner(LocalQwenClient { _, _ -> response(5) }, EnsembleCohesionModelIdentity.DETERMINISTIC).plan(input)

        assertEquals(5, plan.boundaries.size)
        assertTrue(plan.boundaries.zip(input.boundaries).all { (bridge, context) ->
            bridge.instrument in context.roles.supported && bridge.outgoingInstanceId == context.outgoingInstanceId && bridge.incomingInstanceId == context.incomingInstanceId
        })
    }

    @Test fun `role bridge merge ducks an exact same-pitch overlay instead of stacking attacks`() {
        val base = input("A1" to "A2")
        val input = base.copy(occurrences = listOf(
            SongOccurrenceEvidence("A1", base.boundaries.single().outgoing),
            SongOccurrenceEvidence("A2", base.boundaries.single().incoming)
        ))
        val plan = plan(input); val bridge = plan.boundaries.single()
        val bridgePath = root.resolve(EnsembleCohesionStore.bridgeMidi("A1", "A2"))
        DeterministicTransitionBridgeEngine.write(bridgePath, input, input.boundaries.single(), bridge)
        val source = root.resolve("midi/generated/drums.mid"); writeNote(source, 36, 3_300, 3_400)
        val output = root.resolve("cohesion/drums.mid")

        CohesionRoleBridgeApplier.write(root, source, output, "drums", input, plan)

        val ranges = pairedNotes(output).filter { it.first == 36 }.map { it.second to it.third }.sortedBy { it.first }
        assertTrue(ranges.zipWithNext().all { (left, right) -> left.second <= right.first })
    }

    @Test fun `sustained texture moves an exact foreground melody unison by octave`() {
        val base = input("A1" to "A2", notes = listOf(CohesionMelodyNote("outgoing-melody", 0, 60, 96, 3_360, 3_840)))
        val boundary = base.boundaries.single().copy(
            allowedRoleActions = listOf(TransitionRoleAction.SUSTAINED_TEXTURE),
            transitionPolicy = base.boundaries.single().transitionPolicy.copy(allowedActions = listOf(TransitionRoleAction.SUSTAINED_TEXTURE)),
            roles = TransitionBoundaryRoleEvidence(emptyList(), listOf("strings"), listOf("strings"), emptyList(), emptyList(), listOf("strings"))
        )
        val input = base.copy(supportedInstruments = listOf("strings"), boundaries = listOf(boundary))
        val plan = TransitionBridgePlan(
            outgoingInstanceId = "A1", incomingInstanceId = "A2", outgoingHash = boundary.outgoing.sourceHash, incomingHash = boundary.incoming.sourceHash,
            arrangementSha256 = input.arrangementSha256, contextSha256 = input.contextSha256,
            roleAction = TransitionRoleAction.SUSTAINED_TEXTURE, bridgeType = BridgeType.PAD_SUSTAIN, instrument = "strings",
            harmonicHandoff = HarmonicHandoff.HOLD, rhythmicGesture = RhythmicGesture.SUSTAIN, energyContour = EnergyContour.HOLD,
            rationale = "Keep the texture below the melody", leadBeats = 1
        )
        val path = root.resolve("texture.mid")

        DeterministicTransitionBridgeEngine.write(path, input, boundary, plan)

        assertEquals(listOf(72), pairedNotes(path).map(Triple<Int, Long, Long>::first))
    }

    @Test fun `step-to-incoming bass pickup retains the outgoing chord until the boundary`() {
        val base = input("A1" to "A2")
        val original = base.boundaries.single()
        val boundary = original.copy(
            outgoing = original.outgoing.copy(chords = listOf(MidiChord(0, 3_840, "G", 1.0))),
            incoming = original.incoming.copy(chords = listOf(MidiChord(0, 3_840, "C", 1.0))),
            allowedRoleActions = listOf(TransitionRoleAction.BASS_MOTION),
            transitionPolicy = original.transitionPolicy.copy(allowedActions = listOf(TransitionRoleAction.BASS_MOTION)),
            roles = TransitionBoundaryRoleEvidence(listOf("bass"), listOf("bass"), emptyList(), emptyList(), listOf("bass"), listOf("bass"))
        )
        val input = base.copy(supportedInstruments = listOf("bass"), boundaries = listOf(boundary))
        val plan = TransitionBridgePlan(
            outgoingInstanceId = "A1", incomingInstanceId = "A2", outgoingHash = boundary.outgoing.sourceHash, incomingHash = boundary.incoming.sourceHash,
            arrangementSha256 = input.arrangementSha256, contextSha256 = input.contextSha256,
            roleAction = TransitionRoleAction.BASS_MOTION, bridgeType = BridgeType.BASS_WALK, instrument = "bass",
            harmonicHandoff = HarmonicHandoff.STEP_TO_INCOMING, rhythmicGesture = RhythmicGesture.PICKUP, energyContour = EnergyContour.HOLD,
            rationale = "Lead into the next section"
        )
        val path = root.resolve("bass-pickup.mid")

        DeterministicTransitionBridgeEngine.write(path, input, boundary, plan)

        assertEquals(listOf(43), pairedNotes(path).map(Triple<Int, Long, Long>::first))
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
        val response = """{"boundaries":[{"roleAction":"DRUM_FILL","harmonicHandoff":"HOLD","rhythmicGesture":"FILL","energyContour":"RISE","rationale":"${"Carry the groove forward! ".repeat(12)}"}]}"""

        val plan = LocalQwenEnsembleCohesionPlanner(LocalQwenClient { _, _ -> response }, EnsembleCohesionModelIdentity.DETERMINISTIC).plan(input)

        assertTrue(plan.boundaries.single().rationale.matches(Regex("[A-Za-z0-9 ,.'-]{1,180}")))
        assertEquals(TransitionRoleAction.DRUM_FILL, plan.boundaries.single().roleAction)
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
            boundaries = boundaries.mapIndexed { index, (outgoing, incoming) ->
                TransitionContext(
                    outgoing, incoming, occurrenceEvidence.getValue(outgoing), occurrenceEvidence.getValue(incoming),
                    listOf(TransitionRoleAction.DRUM_FILL), policy(context),
                    TransitionBoundaryRoleEvidence(listOf("drums"), listOf("drums"), emptyList(), emptyList(), listOf("drums"), listOf("drums")),
                    index * 3_840L, (index + 1L) * 3_840L
                )
            },
            acceptedFullSongGrooveMap = FullSongGrooveMap(
                ppq = 480, meterDenominator = 4, subdivisionsPerBeat = 4,
                points = occurrenceEvidence.keys.sorted().flatMapIndexed { index, id ->
                    listOf(FullSongGroovePoint(id, 7, 0, index * 3_840L + 3_360L, 0L))
                }, occurrenceTemplateFingerprints = emptyList(), boundaries = emptyList(), maximumUnreviewedDiscontinuityTicks = 30L
            )
        )
    }

    private fun plan(input: EnsembleCohesionInput, override: TransitionBridgePlan? = null) = EnsembleCohesionPlan(
        inputHash = input.inputHash, arrangementSha256 = input.arrangementSha256, contextSha256 = input.contextSha256,
        model = EnsembleCohesionModelIdentity.DETERMINISTIC,
        boundaries = input.boundaries.map { boundary -> override ?: TransitionBridgePlan(
            outgoingInstanceId = boundary.outgoingInstanceId, incomingInstanceId = boundary.incomingInstanceId,
            outgoingHash = boundary.outgoing.sourceHash, incomingHash = boundary.incoming.sourceHash,
            arrangementSha256 = input.arrangementSha256, contextSha256 = input.contextSha256,
            roleAction = TransitionRoleAction.DRUM_FILL, bridgeType = BridgeType.DRUM_FILL, instrument = "drums",
            harmonicHandoff = HarmonicHandoff.HOLD, rhythmicGesture = RhythmicGesture.FILL,
            energyContour = EnergyContour.RISE, rationale = "Carry energy forward"
        ) }
    )

    private fun response(boundaries: Int): String = """{"boundaries":[${List(boundaries) {
        """{"roleAction":"DRUM_FILL","harmonicHandoff":"HOLD","rhythmicGesture":"FILL","energyContour":"RISE","rationale":"Carry energy forward"}"""
    }.joinToString(",")}]}"""

    private fun policy(hash: String) = TransitionPolicyEvidence("lofi", "calm", hash, listOf(TransitionRoleAction.DRUM_FILL))
    private fun evidence(partId: String, sourceHash: String, notes: List<CohesionMelodyNote>) = TransitionMusicalEvidence(
        partId, sourceHash, "f".repeat(64), 480, 3_840, MidiKey("C", "major", 1.0), emptyList(), MidiTempoChange(0, 80.0), MidiTimeSignature(0, 4, 4), 0.5,
        TransitionBoundarySummary(true, true, 0, 1_440), TransitionArrangementEvidence("9".repeat(64), SongSectionPurpose.DEVELOPMENT, listOf(TransitionInstrumentEvidence("drums", "DrumsInstrumentPlan", 0.5)), "8".repeat(64)), notes
    )
    private fun writeNote(path: Path, pitch: Int, start: Long, end: Long) {
        Files.createDirectories(requireNotNull(path.parent)); val sequence = Sequence(Sequence.PPQ, 480); val track = sequence.createTrack()
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 9, pitch, 90), start))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 9, pitch, 0), end))
        MidiSystem.write(sequence, 1, path.toFile())
    }
    private fun pairedNotes(path: Path): List<Triple<Int, Long, Long>> {
        val active = mutableMapOf<Pair<Int, Int>, ArrayDeque<Long>>(); val result = mutableListOf<Triple<Int, Long, Long>>()
        MidiSystem.getSequence(path.toFile()).tracks.forEach { track -> (0 until track.size()).forEach { index ->
            val event = track[index]; val message = event.message as? ShortMessage ?: return@forEach; val key = message.channel to message.data1
            if (message.command == ShortMessage.NOTE_ON && message.data2 > 0) active.getOrPut(key) { ArrayDeque() }.addLast(event.tick)
            else if (message.command == ShortMessage.NOTE_OFF || message.command == ShortMessage.NOTE_ON && message.data2 == 0) active[key]?.removeFirstOrNull()?.let { result += Triple(message.data1, it, event.tick) }
        } }
        return result
    }
}
