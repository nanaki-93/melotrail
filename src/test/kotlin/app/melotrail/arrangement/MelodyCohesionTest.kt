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

class MelodyCohesionTest {
    @TempDir lateinit var tempDir: Path

    @Test
    fun `repeated source occurrences receive independent bounded derived edits`() {
        val input = input()
        val plan = plan(input, listOf(
            MelodyOccurrencePlan("A1", "A", HASH, listOf(MelodyTranspose(semitones = 2), MelodyBoundaryEdit(0, 480, startDeltaTicks = 30, endDeltaTicks = -30)), rationale = "Raise the second entry"),
            MelodyOccurrencePlan("A2", "A", HASH, listOf(MelodyTiming(shiftTicks = 120), MelodyPatch(1_680, 1_920, listOf(MelodyPatchNote(0, 67, 90, 1_680, 1_800)))), rationale = "Add a bounded pickup")
        ))

        val source = listOf(MidiNote(0, 60, 80, 0, 480), MidiNote(0, 64, 80, 960, 1_440))
        val engine = MelodyCohesionTransformationEngine()
        val first = engine.transform(source, plan.occurrences[0])
        val second = engine.transform(source, plan.occurrences[1])

        assertEquals(listOf(62, 66), first.notes.map { it.pitch })
        assertEquals(30L, first.notes.first().startTick)
        assertEquals(listOf(120L, 1_080L, 1_680L), second.notes.map { it.startTick })
        assertEquals(listOf(60, 64), source.map { it.pitch })
        assertTrue(first.audit.any { it.action == "transpose" })
        assertTrue(first.audit.any { it.action == "boundary" })
        assertTrue(second.audit.any { it.action == "patch" })
    }

    @Test
    fun `validator rejects stale unsafe and excessive model output`() {
        val input = input()
        val valid = plan(input, input.occurrences.map { MelodyOccurrencePlan(it.instanceId, it.partId, it.sourceHash, rationale = "Preserve melody") })
        assertTrue(valid.validate(input).isValid)

        val stale = valid.copy(inputHash = "c".repeat(64))
        val excessiveTranspose = valid.copy(occurrences = valid.occurrences.mapIndexed { index, item -> if (index == 0) item.copy(edits = listOf(MelodyTranspose(semitones = 13))) else item })
        val overlapping = valid.copy(occurrences = valid.occurrences.mapIndexed { index, item -> if (index == 0) item.copy(edits = listOf(MelodyPatch(0, 400, listOf(MelodyPatchNote(0, 60, 80, 0, 60))), MelodyPatch(300, 600, listOf(MelodyPatchNote(0, 62, 80, 300, 360))))) else item })
        val outOfRange = valid.copy(occurrences = valid.occurrences.mapIndexed { index, item ->
            if (index == 0) item.copy(edits = listOf(MelodyPatch(0, 480, listOf(MelodyPatchNote(0, 128, 80, 0, 60))))) else item
        })

        listOf(stale, excessiveTranspose, overlapping, outOfRange).forEach { assertFalse(it.validate(input).isValid) }

        val json = Json { encodeDefaults = true }
        val unsafe = json.encodeToString(valid).replace("\"version\":1", "\"version\":1,\"path\":\"/tmp/escape\"")
        assertThrows(IllegalArgumentException::class.java) {
            LocalQwenMelodyCohesionPlanner(FixtureClient(unsafe), CohesionModelIdentity.DETERMINISTIC).plan(input)
        }
    }

    @Test
    fun `repair removes invalid and colliding notes and approval is atomic`() {
        val input = input()
        val plan = plan(input, input.occurrences.map { occurrence ->
            MelodyOccurrencePlan(occurrence.instanceId, occurrence.partId, occurrence.sourceHash, listOf(MelodyRemoveInvalidOrColliding()), rationale = "Remove collisions")
        })
        MelodyCohesionStore.writeDraft(tempDir, input, plan)
        val source = listOf(MidiNote(0, 60, 80, 0, 480), MidiNote(0, 60, 80, 240, 720), MidiNote(0, 62, 80, 960, 1_440))
        val approved = MelodyCohesionStore.approve(tempDir, input, mapOf("A1" to source, "A2" to source))

        assertTrue(Files.isRegularFile(approved))
        assertTrue(Files.isRegularFile(tempDir.resolve(MelodyCohesionStore.AUDIT_FILE)))
        input.occurrences.forEach { occurrence ->
            assertTrue(Files.isRegularFile(MelodyCohesionStore.derivedMidi(tempDir, occurrence.instanceId)))
            assertTrue(MidiPartAnalyzer().analyze(MelodyCohesionStore.derivedMidi(tempDir, occurrence.instanceId), occurrence.partId).noteCount == 2)
        }
        MelodyCohesionStore.reject(tempDir, input)
        assertTrue(Files.isRegularFile(approved))
    }

    @Test
    fun `commercial approval is enforced and deterministic fallback remains valid`() {
        val input = input()
        val fallback = DeterministicMelodyCohesionPlanner().plan(input)
        assertTrue(fallback.validate(input).isValid)
        MelodyCohesionStore.writeDraft(tempDir, input, fallback)
        assertThrows(IllegalArgumentException::class.java) {
            MelodyCohesionStore.approve(tempDir, input, input.occurrences.associate { it.instanceId to emptyList<MidiNote>() }, commercial = true) { false }
        }
        assertFalse(Files.exists(tempDir.resolve(MelodyCohesionStore.APPROVED_FILE)))
    }

    private fun input() = MelodyCohesionInput(
        inputHash = INPUT_HASH,
        occurrences = listOf("A1", "A2").map { id ->
            MelodyOccurrenceInput(id, "A", HASH, 480, 1_920, MidiIntRange(48, 72), MidiKey("C", "major", 0.8), listOf(MidiTempoChange(0, 120.0)), listOf(MidiTimeSignature(0, 4, 4)), emptyList(), 0.5, MelodyBoundarySummary(true, true, 0, 1_440))
        }
    )

    private fun plan(input: MelodyCohesionInput, occurrences: List<MelodyOccurrencePlan>) = MelodyCohesionPlan(inputHash = input.inputHash, model = CohesionModelIdentity.DETERMINISTIC, occurrences = occurrences)
    private class FixtureClient(private val response: String) : LocalQwenClient { override fun complete(systemPrompt: String, userPrompt: String) = response }
    private companion object {
        val HASH = "a".repeat(64)
        val INPUT_HASH = "b".repeat(64)
    }
}
