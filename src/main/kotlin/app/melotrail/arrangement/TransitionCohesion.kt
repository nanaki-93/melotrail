package app.melotrail.arrangement

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import javax.sound.midi.MetaMessage
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage
import kotlin.math.roundToLong

/** Task 116's path-free, boundary-only model contract. */
@Serializable
data class TransitionCohesionInput(
    val version: Int = VERSION,
    val inputHash: String,
    val structureSha256: String,
    val supportedInstruments: List<String>,
    val boundaries: List<TransitionBoundaryInput>
) { companion object { const val VERSION = 1 } }

@Serializable
data class TransitionBoundaryInput(
    val outgoingInstanceId: String,
    val incomingInstanceId: String,
    val outgoing: TransitionMusicalEvidence,
    val incoming: TransitionMusicalEvidence
)

@Serializable
data class TransitionMusicalEvidence(
    val partId: String,
    val sourceHash: String,
    val analysisHash: String,
    val ppq: Int,
    val durationTicks: Long,
    val key: MidiKey?,
    val chords: List<MidiChord>,
    val tempo: MidiTempoChange,
    val meter: MidiTimeSignature,
    val energy: Double,
    val boundary: MelodyBoundarySummary
)

@Serializable
data class TransitionCohesionPlan(
    val version: Int = TransitionCohesionInput.VERSION,
    val inputHash: String,
    val model: CohesionModelIdentity,
    val boundaries: List<TransitionBridgePlan>
)

/** The local runtime, rather than the untrusted response, owns model provenance. */
@Serializable
private data class TransitionCohesionModelResponse(
    val version: Int = TransitionCohesionInput.VERSION,
    val inputHash: String,
    val boundaries: List<TransitionBridgePlan>
)

@Serializable
data class TransitionBridgePlan(
    val outgoingInstanceId: String,
    val incomingInstanceId: String,
    val outgoingHash: String,
    val incomingHash: String,
    val bridgeType: BridgeType,
    val bars: Int,
    val instrument: String,
    val harmonicHandoff: HarmonicHandoff,
    val rhythmicGesture: RhythmicGesture,
    val energyContour: EnergyContour,
    val tempoHandoff: TimingHandoff = TimingHandoff.PRESERVE,
    val meterHandoff: TimingHandoff = TimingHandoff.PRESERVE,
    val rationale: String
)

@Serializable enum class BridgeType { DRUM_FILL, BASS_WALK, PAD_SUSTAIN, BUILD }
@Serializable enum class HarmonicHandoff { HOLD, STEP_TO_INCOMING }
@Serializable enum class RhythmicGesture { FILL, PICKUP, SUSTAIN }
@Serializable enum class EnergyContour { HOLD, RISE, FALL }
@Serializable enum class TimingHandoff { PRESERVE }

data class TransitionCohesionValidationResult(val errors: List<String>) { val isValid get() = errors.isEmpty() }

/** Rejects unknown coverage, stale identities, unbounded values, and unsupported musical handoffs before rendering. */
object TransitionCohesionValidator {
    private val id = Regex("[A-Za-z0-9_-]{1,80}")
    private val hash = Regex("[0-9a-f]{64}")
    private val rationale = Regex("[A-Za-z0-9 ,.'-]{1,180}")

    fun validate(plan: TransitionCohesionPlan, input: TransitionCohesionInput): TransitionCohesionValidationResult {
        val errors = mutableListOf<String>()
        if (input.version != TransitionCohesionInput.VERSION || plan.version != TransitionCohesionInput.VERSION) errors += "Unsupported transition cohesion version"
        if (plan.inputHash != input.inputHash) errors += "Transition cohesion plan input hash is stale"
        val expected = input.boundaries.map { it.outgoingInstanceId to it.incomingInstanceId }
        val actual = plan.boundaries.map { it.outgoingInstanceId to it.incomingInstanceId }
        if (actual != expected) errors += "Transition cohesion plan must contain exactly one boundary in saved Structure order"
        if (actual.distinct().size != actual.size) errors += "Transition cohesion plan contains duplicate boundaries"
        if (input.supportedInstruments.distinct().size != input.supportedInstruments.size) errors += "Transition cohesion input has duplicate instruments"
        input.boundaries.zip(plan.boundaries).forEachIndexed { index, (source, bridge) ->
            val label = "Boundary ${index + 1}"
            if (!id.matches(bridge.outgoingInstanceId) || !id.matches(bridge.incomingInstanceId)) errors += "$label has an invalid occurrence ID"
            if (!hash.matches(bridge.outgoingHash) || !hash.matches(bridge.incomingHash)) errors += "$label has an invalid source hash"
            if (bridge.outgoingHash != source.outgoing.sourceHash || bridge.incomingHash != source.incoming.sourceHash) errors += "$label source hash is stale"
            if (bridge.bars !in 1..2) errors += "$label bridge length must be one or two bars"
            if (bridge.instrument !in input.supportedInstruments) errors += "$label uses unsupported instrument '${bridge.instrument}'"
            if (!rationale.matches(bridge.rationale)) errors += "$label rationale must be bounded musical text"
            if (source.outgoing.ppq != source.incoming.ppq) errors += "$label has unsupported PPQ handoff"
            if (source.outgoing.tempo.bpm != source.incoming.tempo.bpm) errors += "$label has unsupported tempo handoff"
            if (source.outgoing.meter.numerator != source.incoming.meter.numerator || source.outgoing.meter.denominator != source.incoming.meter.denominator) errors += "$label has unsupported meter handoff"
            if (bridge.instrument != requiredInstrument(bridge.bridgeType)) errors += "$label bridge type and instrument do not match"
        }
        return TransitionCohesionValidationResult(errors)
    }

    fun requireValid(plan: TransitionCohesionPlan, input: TransitionCohesionInput) {
        val result = validate(plan, input); require(result.isValid) { result.errors.joinToString("; ") }
    }

    private fun requiredInstrument(type: BridgeType) = when (type) {
        BridgeType.DRUM_FILL, BridgeType.BUILD -> "drums"
        BridgeType.BASS_WALK -> "bass"
        BridgeType.PAD_SUSTAIN -> "pad"
    }
}

/** The model may advise only through this strict JSON seam; it cannot control paths or MIDI events. */
class LocalQwenTransitionCohesionPlanner(
    private val client: LocalQwenClient = LmStudioQwenClient(),
    private val model: CohesionModelIdentity
) {
    fun plan(input: TransitionCohesionInput): TransitionCohesionPlan {
        val response = client.complete(PROMPT, Json { encodeDefaults = true; explicitNulls = false }.encodeToString(TransitionCohesionInput.serializer(), input))
        val parsed = try { Json { ignoreUnknownKeys = false }.decodeFromString(TransitionCohesionModelResponse.serializer(), response) }
        catch (error: Exception) { throw IllegalArgumentException("Qwen returned invalid transition-cohesion JSON: ${error.message}", error) }
        return TransitionCohesionPlan(parsed.version, parsed.inputHash, model, parsed.boundaries).also {
            try { TransitionCohesionValidator.requireValid(it, input) } catch (error: IllegalArgumentException) {
                throw IllegalArgumentException("Qwen returned an invalid transition-cohesion plan: ${error.message}", error)
            }
        }
    }
    private companion object { const val PROMPT = """
        Return one JSON object only: no markdown, prose, reasoning, or fields other than this exact version-1 response schema:
        {
          "version": 1,
          "inputHash": "copy the supplied inputHash exactly",
          "boundaries": [{
            "outgoingInstanceId": "copy the supplied boundary value exactly",
            "incomingInstanceId": "copy the supplied boundary value exactly",
            "outgoingHash": "copy the outgoing sourceHash exactly",
            "incomingHash": "copy the incoming sourceHash exactly",
            "bridgeType": "DRUM_FILL",
            "bars": 1,
            "instrument": "drums",
            "harmonicHandoff": "HOLD",
            "rhythmicGesture": "FILL",
            "energyContour": "RISE",
            "tempoHandoff": "PRESERVE",
            "meterHandoff": "PRESERVE",
            "rationale": "bounded plain musical rationale"
          }]
        }
        The model field is code-owned: never include it. Return exactly one boundary object for every supplied boundary,
        in the supplied order. Copy each outgoing/incoming ID and source hash exactly. bars is 1 or 2. Use exactly one
        supplied instrument and keep it compatible with bridgeType: DRUM_FILL or BUILD uses drums; BASS_WALK uses bass;
        PAD_SUSTAIN uses pad. Allowed bridgeType values are DRUM_FILL, BASS_WALK, PAD_SUSTAIN, BUILD. Allowed
        harmonicHandoff values are HOLD, STEP_TO_INCOMING; rhythmicGesture values are FILL, PICKUP, SUSTAIN;
        energyContour values are HOLD, RISE, FALL. tempoHandoff and meterHandoff must both be PRESERVE. rationale is
        1 to 180 plain characters using only letters, digits, spaces, commas, periods, apostrophes, and hyphens.
        Do not use bridgeId, bridges, instruments, durationBars, tempo, meter, any other key, paths, commands,
        plugins, code, MIDI notes, or arbitrary instruments.
    """ }
}

/** Code-owned bridge generator. It has no model-defined notes, paths, or timing values. */
object DeterministicTransitionBridgeEngine {
    fun write(path: Path, input: TransitionBoundaryInput, plan: TransitionBridgePlan) {
        val ppq = input.incoming.ppq
        val meter = input.incoming.meter
        val bar = ppq * 4L / meter.denominator * meter.numerator
        val length = bar * plan.bars
        val sequence = Sequence(Sequence.PPQ, ppq)
        val track = sequence.createTrack()
        addTempo(track, input.incoming.tempo, 0)
        addMeter(track, meter, 0)
        val root = keyPitch(input.incoming.key)
        when (plan.bridgeType) {
            BridgeType.DRUM_FILL, BridgeType.BUILD -> {
                val step = (ppq / 2).toLong().coerceAtLeast(1)
                generateSequence(0L) { it + step }.takeWhile { it < length }.forEachIndexed { index, tick ->
                    val pitch = if (index % 2 == 0) 36 else 38; note(track, 9, pitch, 72 + (index % 4) * 10, tick, (tick + step / 2).coerceAtMost(length))
                }
            }
            BridgeType.BASS_WALK -> {
                val step = ppq.toLong(); listOf(0, 2, 4, 5).forEachIndexed { index, interval ->
                    val start = index * step; if (start < length) note(track, 0, 36 + root + interval, 82, start, (start + step).coerceAtMost(length))
                }
            }
            BridgeType.PAD_SUSTAIN -> note(track, 0, 60 + root, 68, 0, length)
        }
        track.add(MidiEvent(MetaMessage(0x2F, byteArrayOf(), 0), length))
        publishMidi(path, sequence, input, length)
    }

    private fun publishMidi(path: Path, sequence: Sequence, input: TransitionBoundaryInput, length: Long) {
        Files.createDirectories(path.parent)
        val temporary = path.resolveSibling(".${path.fileName}.tmp")
        try {
            require(MidiSystem.write(sequence, 1, temporary.toFile()) > 0) { "Could not write transition MIDI" }
            val reread = MidiSystem.getSequence(temporary.toFile())
            require(reread.divisionType == Sequence.PPQ && reread.resolution == input.incoming.ppq) { "Transition MIDI round-trip timing mismatch" }
            val notes = reread.tracks.flatMap { track -> (0 until track.size()).map(track::get) }.filter { it.message is ShortMessage }
            val on = notes.count { (it.message as ShortMessage).command == ShortMessage.NOTE_ON && (it.message as ShortMessage).data2 > 0 }
            val off = notes.count { val m = it.message as ShortMessage; m.command == ShortMessage.NOTE_OFF || (m.command == ShortMessage.NOTE_ON && m.data2 == 0) }
            require(on > 0 && on == off && reread.tickLength >= length) { "Transition MIDI has invalid note pairs or timing" }
            move(temporary, path)
        } finally { Files.deleteIfExists(temporary) }
    }
    private fun note(track: javax.sound.midi.Track, channel: Int, pitch: Int, velocity: Int, start: Long, end: Long) { track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, channel, pitch, velocity), start)); track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, channel, pitch, 0), end)) }
    private fun addTempo(track: javax.sound.midi.Track, tempo: MidiTempoChange, tick: Long) { val micros = (60_000_000.0 / tempo.bpm).roundToLong().toInt(); track.add(MidiEvent(MetaMessage(0x51, byteArrayOf((micros ushr 16).toByte(), (micros ushr 8).toByte(), micros.toByte()), 3), tick)) }
    private fun addMeter(track: javax.sound.midi.Track, meter: MidiTimeSignature, tick: Long) { track.add(MidiEvent(MetaMessage(0x58, byteArrayOf(meter.numerator.toByte(), Integer.numberOfTrailingZeros(meter.denominator).toByte(), 24, 8), 4), tick)) }
    private fun keyPitch(key: MidiKey?): Int = key?.toMusicalKeyOrNull()?.tonic?.chromatic ?: 0
    private fun move(from: Path, to: Path) { try { Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) } catch (_: AtomicMoveNotSupportedException) { Files.move(from, to, StandardCopyOption.REPLACE_EXISTING) } }
}

/** Atomic, project-confined bridge records. Preview audio is deliberately a separate renderer boundary. */
object TransitionCohesionStore {
    const val DRAFT_FILE = "cohesion/cohesion.draft.json"
    const val APPROVED_FILE = "cohesion/cohesion.json"
    private val json = Json { prettyPrint = true; encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = false }
    fun bridgeMidi(outgoing: String, incoming: String) = "cohesion/boundaries/$outgoing--$incoming/bridge.mid"
    fun audit(outgoing: String, incoming: String) = "cohesion/boundaries/$outgoing--$incoming/audit.json"

    fun writeDraft(root: Path, input: TransitionCohesionInput, plan: TransitionCohesionPlan): Path {
        TransitionCohesionValidator.requireValid(plan, input)
        plan.boundaries.forEachIndexed { index, bridge ->
            val boundary = input.boundaries[index]
            val midi = root.resolve(bridgeMidi(bridge.outgoingInstanceId, bridge.incomingInstanceId))
            DeterministicTransitionBridgeEngine.write(midi, boundary, bridge)
            atomicWrite(root.resolve(audit(bridge.outgoingInstanceId, bridge.incomingInstanceId)), json.encodeToString(TransitionBridgePlan.serializer(), bridge))
        }
        val text = json.encodeToString(TransitionCohesionPlan.serializer(), plan)
        return atomicWrite(root.resolve(DRAFT_FILE), text).also { persist(root, input, plan, approved = false, reviewed = emptySet()) }
    }
    fun readDraft(root: Path, input: TransitionCohesionInput): TransitionCohesionPlan = read(root.resolve(DRAFT_FILE), input)
    fun readApproved(root: Path, input: TransitionCohesionInput): TransitionCohesionPlan = read(root.resolve(APPROVED_FILE), input)
    fun markReviewed(root: Path, input: TransitionCohesionInput, outgoing: String, incoming: String): Set<Pair<String, String>> {
        val plan = readDraft(root, input); val pair = outgoing to incoming
        require(pair in plan.boundaries.map { it.outgoingInstanceId to it.incomingInstanceId }) { "Unknown cohesion boundary $outgoing -> $incoming" }
        val existing = reviewed(root, input); val updated = existing + pair
        persist(root, input, plan, false, updated); return updated
    }
    fun approve(root: Path, input: TransitionCohesionInput): Path {
        val plan = readDraft(root, input); val expected = plan.boundaries.map { it.outgoingInstanceId to it.incomingInstanceId }.toSet()
        require(reviewed(root, input) == expected) { "Review every current cohesion boundary before aggregate approval." }
        val text = json.encodeToString(TransitionCohesionPlan.serializer(), plan)
        return atomicWrite(root.resolve(APPROVED_FILE), text).also { persist(root, input, plan, true, expected) }
    }
    fun reject(root: Path, input: TransitionCohesionInput): Path = atomicWrite(root.resolve("cohesion/rejected-${input.inputHash}.json"), Files.readString(root.resolve(DRAFT_FILE)))
    fun isApprovedCurrent(root: Path, input: TransitionCohesionInput): Boolean = runCatching {
        val workflow = ProjectStore.read(root).workflow.cohesion ?: return false
        workflow.approved && workflow.inputSha256 == input.inputHash && workflow.structureSha256 == input.structureSha256 &&
            workflow.boundaries.map { it.outgoingInstanceId to it.incomingInstanceId } == input.boundaries.map { it.outgoingInstanceId to it.incomingInstanceId } &&
            workflow.boundaries.all { it.approved != null && Files.isRegularFile(root.resolve(bridgeMidi(it.outgoingInstanceId, it.incomingInstanceId))) }
    }.getOrDefault(false)
    private fun reviewed(root: Path, input: TransitionCohesionInput): Set<Pair<String, String>> = ProjectStore.read(root).workflow.cohesion?.boundaries.orEmpty().filter { it.approved != null }.map { it.outgoingInstanceId to it.incomingInstanceId }.toSet()
    private fun read(path: Path, input: TransitionCohesionInput) = json.decodeFromString(TransitionCohesionPlan.serializer(), Files.readString(path, StandardCharsets.UTF_8)).also { TransitionCohesionValidator.requireValid(it, input) }
    private fun persist(root: Path, input: TransitionCohesionInput, plan: TransitionCohesionPlan, approved: Boolean, reviewed: Set<Pair<String, String>>) {
        val text = json.encodeToString(TransitionCohesionPlan.serializer(), plan)
        val boundaries = plan.boundaries.map { bridge ->
            val pair = bridge.outgoingInstanceId to bridge.incomingInstanceId
            val draft = CohesionBoundaryArtifactPaths.draft(bridge.outgoingInstanceId, bridge.incomingInstanceId)
            atomicWrite(root.resolve(draft), json.encodeToString(TransitionBridgePlan.serializer(), bridge))
            val approvedFile = CohesionBoundaryArtifactPaths.approved(bridge.outgoingInstanceId, bridge.incomingInstanceId)
            if (pair in reviewed) atomicWrite(root.resolve(approvedFile), json.encodeToString(TransitionBridgePlan.serializer(), bridge))
            CohesionBoundaryReference(bridge.outgoingInstanceId, bridge.incomingInstanceId, input.inputHash,
                WorkflowArtifactReference(draft, digest(root.resolve(draft))),
                if (pair in reviewed) WorkflowArtifactReference(approvedFile, digest(root.resolve(approvedFile))) else null)
        }
        val project = ProjectStore.read(root); if (project.version != Project.CURRENT_VERSION) return
        val workflow = project.workflow.invalidate(WorkflowChange.COHESION).markCurrent(WorkflowArtifact.COHESION).copy(
            cohesion = CohesionWorkflowReferences(input.inputHash, WorkflowArtifactReference(if (approved) APPROVED_FILE else DRAFT_FILE, digestText(text)), emptyList(), approved, boundaries, input.structureSha256)
        )
        ProjectStore.write(root, project.copy(workflow = workflow))
    }
    private fun atomicWrite(path: Path, text: String): Path { Files.createDirectories(path.parent); val tmp = path.resolveSibling(".${path.fileName}.tmp"); try { Files.writeString(tmp, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING); try { Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) } catch (_: AtomicMoveNotSupportedException) { Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING) }; return path } finally { Files.deleteIfExists(tmp) } }
    private fun digest(path: Path) = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
    private fun digestText(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

object TransitionCohesionInputFactory {
    fun from(input: MelodyCohesionInput, supportedInstruments: List<String> = listOf("drums", "bass", "pad")): TransitionCohesionInput {
        val byId = input.occurrences.associateBy { it.instanceId }
        val boundaries = input.boundaries.map { pair -> TransitionBoundaryInput(pair.outgoingInstanceId, pair.incomingInstanceId, evidence(byId.getValue(pair.outgoingInstanceId)), evidence(byId.getValue(pair.incomingInstanceId))) }
        val seed = TransitionCohesionInput(inputHash = "", structureSha256 = input.structureSha256, supportedInstruments = supportedInstruments.sorted(), boundaries = boundaries)
        val hash = MessageDigest.getInstance("SHA-256").digest(Json { encodeDefaults = true; explicitNulls = false }.encodeToString(TransitionCohesionInput.serializer(), seed).toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
        return seed.copy(inputHash = hash)
    }
    private fun evidence(source: MelodyOccurrenceInput) = TransitionMusicalEvidence(source.partId, source.sourceHash, source.analysisSha256, source.ppq, source.durationTicks, source.key, source.chords, source.tempoMap.first(), source.timeSignatures.first(), source.energy, source.boundarySummary)
}
