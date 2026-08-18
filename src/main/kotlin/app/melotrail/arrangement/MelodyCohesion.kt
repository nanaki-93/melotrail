package app.melotrail.arrangement

import app.melotrail.licensing.ModelId
import app.melotrail.licensing.ModelRegistry
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
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

/**
 * Task 069's deliberately narrow, data-only boundary.  A plan names stable
 * structure occurrences and bounded musical edits; it never carries a path,
 * command, instrument name, or raw MIDI event stream from a model.
 */
@Serializable
data class MelodyCohesionInput(
    val version: Int = CURRENT_VERSION,
    val inputHash: String,
    /** Fingerprint of the saved, ordered Structure occurrence sequence. */
    val structureSha256: String = "",
    val occurrences: List<MelodyOccurrenceInput>,
    /** The exact adjacent pairs from [occurrences], in saved Structure order. */
    val boundaries: List<MelodyCohesionBoundaryInput> = emptyList()
) {
    companion object { const val CURRENT_VERSION = 1 }
}

@Serializable
data class MelodyOccurrenceInput(
    val instanceId: String,
    val partId: String,
    val sourceHash: String,
    val ppq: Int,
    val durationTicks: Long,
    val pitchRange: MidiIntRange?,
    val key: MidiKey?,
    val tempoMap: List<MidiTempoChange>,
    val timeSignatures: List<MidiTimeSignature>,
    val chords: List<MidiChord>,
    val energy: Double,
    val boundarySummary: MelodyBoundarySummary,
    /** Fingerprint of the current analysis bound to the selected source MIDI. */
    val analysisSha256: String = ""
)

/** One ordered Structure handoff edge. This has no model-controlled fields. */
@Serializable
data class MelodyCohesionBoundaryInput(
    val outgoingInstanceId: String,
    val incomingInstanceId: String
)

@Serializable
data class MelodyBoundarySummary(val startsWithSound: Boolean, val endsWithSound: Boolean, val firstNoteTick: Long?, val lastNoteEndTick: Long?)

@Serializable
data class MelodyCohesionPlan(
    val version: Int = CURRENT_VERSION,
    val inputHash: String,
    val model: CohesionModelIdentity,
    val occurrences: List<MelodyOccurrencePlan>
) {
    fun validate(input: MelodyCohesionInput): MelodyCohesionValidationResult = MelodyCohesionValidator.validate(this, input)
    fun requireValid(input: MelodyCohesionInput) = require(validate(input).isValid) { validate(input).errors.joinToString("; ") }
    companion object { const val CURRENT_VERSION = 1 }
}

@Serializable
data class CohesionModelIdentity(val name: String, val version: String, val hash: String, val promptContractVersion: Int = 1) {
    init {
        require(SAFE_ID.matches(name) && SAFE_ID.matches(version) && SHA_256.matches(hash)) { "Cohesion model identity is invalid" }
        require(promptContractVersion == 1) { "Unsupported cohesion prompt contract" }
    }
    companion object {
        private val SAFE_ID = Regex("[A-Za-z0-9._-]{1,80}")
        private val SHA_256 = Regex("[a-f0-9]{64}")
        val DETERMINISTIC = CohesionModelIdentity("deterministic", "1", "0".repeat(64))
    }
}

@Serializable
data class MelodyOccurrencePlan(
    val instanceId: String,
    val partId: String,
    val sourceHash: String,
    val edits: List<MelodyEdit> = emptyList(),
    val transition: CohesionTransition = CohesionTransition(),
    val rationale: String
)

@Serializable
data class CohesionTransition(
    val type: CohesionTransitionType = CohesionTransitionType.NONE,
    val energy: Double = 0.5
)

@Serializable
enum class CohesionTransitionType { @SerialName("none") NONE, @SerialName("crossfade") CROSSFADE, @SerialName("bridge") BRIDGE }

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@kotlinx.serialization.json.JsonClassDiscriminator("type")
sealed class MelodyEdit {
    abstract val startTick: Long?
    abstract val endTick: Long?
}

@Serializable
@SerialName("transpose")
data class MelodyTranspose(
    override val startTick: Long? = null,
    override val endTick: Long? = null,
    val semitones: Int
) : MelodyEdit()

@Serializable
@SerialName("timing")
data class MelodyTiming(
    override val startTick: Long? = null,
    override val endTick: Long? = null,
    val shiftTicks: Long = 0,
    val timeScale: Double = 1.0,
    val quantizeTicks: Int? = null
) : MelodyEdit()

@Serializable
@SerialName("boundary")
data class MelodyBoundaryEdit(
    override val startTick: Long,
    override val endTick: Long,
    val startDeltaTicks: Long = 0,
    val endDeltaTicks: Long = 0
) : MelodyEdit()

@Serializable
@SerialName("remove_invalid_or_colliding")
data class MelodyRemoveInvalidOrColliding(
    override val startTick: Long? = null,
    override val endTick: Long? = null
) : MelodyEdit()

@Serializable
@SerialName("patch")
data class MelodyPatch(
    override val startTick: Long,
    override val endTick: Long,
    val notes: List<MelodyPatchNote>
) : MelodyEdit()

@Serializable
data class MelodyPatchNote(val channel: Int, val pitch: Int, val velocity: Int, val startTick: Long, val endTick: Long)

data class MelodyCohesionValidationResult(val errors: List<String>) { val isValid: Boolean get() = errors.isEmpty() }

/** All numeric limits are code-owned and are repeated in the Qwen prompt. */
object MelodyCohesionValidator {
    const val MAX_TRANSPOSE_SEMITONES = 12
    const val MAX_SHIFT_TICKS = 480L
    const val MIN_TIME_SCALE = 0.90
    const val MAX_TIME_SCALE = 1.10
    const val MAX_BOUNDARY_DELTA_TICKS = 480L
    const val MAX_PATCHES_PER_OCCURRENCE = 8
    const val MAX_PATCH_NOTES = 8
    const val MIN_NOTE_TICKS = 30L
    private const val MAX_RATIONALE_LENGTH = 180
    private val safeId = Regex("[A-Za-z0-9_-]{1,80}")
    private val sha256 = Regex("[0-9a-f]{64}")
    private val safeRationale = Regex("[A-Za-z0-9 ,.-]{1,180}")

    fun validate(plan: MelodyCohesionPlan, input: MelodyCohesionInput): MelodyCohesionValidationResult {
        val errors = mutableListOf<String>()
        if (input.version != MelodyCohesionInput.CURRENT_VERSION) errors += "Unsupported cohesion input version"
        validateInput(input, errors)
        if (plan.version != MelodyCohesionPlan.CURRENT_VERSION) errors += "Unsupported cohesion plan version"
        if (plan.inputHash != input.inputHash) errors += "Cohesion plan input hash is stale"
        if (plan.occurrences.size != input.occurrences.size) errors += "Cohesion plan must contain exactly one occurrence plan per structure occurrence"
        if (plan.occurrences.map { it.instanceId }.toSet().size != plan.occurrences.size) errors += "Cohesion plan contains duplicate occurrence IDs"
        val expected = input.occurrences.associateBy { it.instanceId }
        plan.occurrences.forEachIndexed { index, occurrence ->
            val source = expected[occurrence.instanceId]
            val label = "Cohesion occurrence ${index + 1}"
            if (!safeId.matches(occurrence.instanceId) || !safeId.matches(occurrence.partId)) errors += "$label has an invalid occurrence identity"
            if (source == null) errors += "$label targets unknown occurrence '${occurrence.instanceId}'" else {
                if (occurrence.partId != source.partId) errors += "$label part does not match the saved structure"
                if (occurrence.sourceHash != source.sourceHash) errors += "$label source hash is stale"
                validateEdits(label, occurrence.edits, source, errors)
            }
            if (!safeRationale.matches(occurrence.rationale)) errors += "$label rationale must be short, non-executable musical text"
            if (!occurrence.transition.energy.isFinite() || occurrence.transition.energy !in 0.0..1.0) errors += "$label transition energy must be finite and within 0..1"
        }
        return MelodyCohesionValidationResult(errors)
    }

    private fun validateInput(input: MelodyCohesionInput, errors: MutableList<String>) {
        if (input.occurrences.map(MelodyOccurrenceInput::instanceId).distinct().size != input.occurrences.size) {
            errors += "Cohesion input contains duplicate occurrence IDs"
        }
        val expectedBoundaries = input.occurrences.zipWithNext { outgoing, incoming ->
            MelodyCohesionBoundaryInput(outgoing.instanceId, incoming.instanceId)
        }
        if (input.boundaries.isNotEmpty() && input.boundaries != expectedBoundaries) {
            errors += "Cohesion input boundaries do not match the saved structure order"
        }
        // Inputs created before Structure handoff fingerprints remain readable as
        // draft evidence, but the factory below always emits all three bindings.
        if (input.structureSha256.isNotEmpty() && !sha256.matches(input.structureSha256)) {
            errors += "Cohesion structure fingerprint is invalid"
        }
        input.occurrences.forEachIndexed { index, occurrence ->
            if (occurrence.analysisSha256.isNotEmpty() && !sha256.matches(occurrence.analysisSha256)) {
                errors += "Cohesion occurrence ${index + 1} analysis fingerprint is invalid"
            }
        }
    }

    private fun validateEdits(label: String, edits: List<MelodyEdit>, source: MelodyOccurrenceInput, errors: MutableList<String>) {
        if (edits.size > MAX_PATCHES_PER_OCCURRENCE + 4) errors += "$label has too many edits"
        if (edits.count { it is MelodyTiming } > 1) errors += "$label may contain at most one timing edit"
        if (edits.count { it is MelodyRemoveInvalidOrColliding } > 1) errors += "$label may contain at most one repair edit"
        edits.forEachIndexed { position, edit ->
            val editLabel = "$label edit ${position + 1}"
            val start = edit.startTick ?: 0L
            val end = edit.endTick ?: source.durationTicks
            if (start < 0 || end <= start || end > source.durationTicks) errors += "$editLabel targets an invalid tick range"
            when (edit) {
                is MelodyTranspose -> if (edit.semitones !in -MAX_TRANSPOSE_SEMITONES..MAX_TRANSPOSE_SEMITONES || edit.semitones == 0) errors += "$editLabel transpose must be non-zero and within ±$MAX_TRANSPOSE_SEMITONES semitones"
                is MelodyTiming -> {
                    if (kotlin.math.abs(edit.shiftTicks) > MAX_SHIFT_TICKS) errors += "$editLabel timing shift exceeds $MAX_SHIFT_TICKS ticks"
                    if (!edit.timeScale.isFinite() || edit.timeScale !in MIN_TIME_SCALE..MAX_TIME_SCALE) errors += "$editLabel time scale must be within $MIN_TIME_SCALE..$MAX_TIME_SCALE"
                    if (edit.quantizeTicks != null && edit.quantizeTicks !in setOf(60, 120, 240, 480)) errors += "$editLabel quantization must be one of 60, 120, 240, or 480 ticks"
                }
                is MelodyBoundaryEdit -> if (kotlin.math.abs(edit.startDeltaTicks) > MAX_BOUNDARY_DELTA_TICKS || kotlin.math.abs(edit.endDeltaTicks) > MAX_BOUNDARY_DELTA_TICKS) errors += "$editLabel boundary change exceeds $MAX_BOUNDARY_DELTA_TICKS ticks"
                is MelodyPatch -> {
                    if (edits.count { it is MelodyPatch } > MAX_PATCHES_PER_OCCURRENCE || edit.notes.isEmpty() || edit.notes.size > MAX_PATCH_NOTES) errors += "$editLabel has an invalid patch size"
                    edit.notes.forEach { note ->
                        if (note.channel !in 0..15 || note.pitch !in 0..127 || note.velocity !in 1..127 || note.startTick < edit.startTick || note.endTick > edit.endTick || note.endTick - note.startTick < MIN_NOTE_TICKS) errors += "$editLabel contains an invalid patch note"
                    }
                }
                is MelodyRemoveInvalidOrColliding -> Unit
            }
        }
        listOf(MelodyTranspose::class, MelodyBoundaryEdit::class, MelodyPatch::class).forEach { type ->
            val ranges = edits.filter { type.isInstance(it) }.map { (it.startTick ?: 0L) to (it.endTick ?: source.durationTicks) }.sortedBy { it.first }
            if (ranges.zipWithNext().any { (left, right) -> right.first < left.second }) errors += "$label has overlapping ${type.simpleName} ranges"
        }
    }
}

data class MelodyTransformationResult(val notes: List<MidiNote>, val audit: List<MelodyAuditEntry>)
@Serializable data class MelodyAuditEntry(val occurrenceId: String, val action: String, val sourceOccurrence: String, val detail: String, val rationale: String)
@Serializable data class MelodyCohesionProvenance(
    val inputHash: String,
    val outputHash: String,
    val model: CohesionModelIdentity,
    val approved: Boolean
)

/** Pure deterministic transformation. It is the only code that changes notes. */
class MelodyCohesionTransformationEngine {
    fun transform(source: List<MidiNote>, occurrence: MelodyOccurrencePlan): MelodyTransformationResult {
        var notes = source.sortedWith(noteOrder).toMutableList()
        val audit = mutableListOf<MelodyAuditEntry>()
        occurrence.edits.forEach { edit -> when (edit) {
            is MelodyTranspose -> {
                notes = notes.map { note -> if (inRange(note.startTick, edit.startTick, edit.endTick)) note.copy(pitch = note.pitch + edit.semitones) else note }.toMutableList()
                audit += entry(occurrence, "transpose", "${edit.semitones} semitones")
            }
            is MelodyTiming -> {
                val anchor = edit.startTick ?: 0L
                notes = notes.map { note -> if (inRange(note.startTick, edit.startTick, edit.endTick)) {
                    val start = aligned(anchor + ((note.startTick - anchor) * edit.timeScale).roundToLong() + edit.shiftTicks, edit.quantizeTicks)
                    val end = aligned(anchor + ((note.endTick - anchor) * edit.timeScale).roundToLong() + edit.shiftTicks, edit.quantizeTicks)
                    note.copy(startTick = start, endTick = end)
                } else note }.toMutableList()
                audit += entry(occurrence, "timing", "shift ${edit.shiftTicks} ticks, scale ${edit.timeScale}")
            }
            is MelodyBoundaryEdit -> {
                notes = notes.map { note -> if (note.endTick > edit.startTick && note.startTick < edit.endTick) note.copy(startTick = note.startTick + edit.startDeltaTicks, endTick = note.endTick + edit.endDeltaTicks) else note }.toMutableList()
                audit += entry(occurrence, "boundary", "start ${edit.startDeltaTicks} ticks, end ${edit.endDeltaTicks} ticks")
            }
            is MelodyPatch -> {
                notes += edit.notes.map { MidiNote(it.channel, it.pitch, it.velocity, it.startTick, it.endTick) }
                audit += entry(occurrence, "patch", "${edit.notes.size} generated bounded note(s)")
            }
            is MelodyRemoveInvalidOrColliding -> Unit
        }}
        val repair = occurrence.edits.any { it is MelodyRemoveInvalidOrColliding }
        val valid = notes.filter { it.pitch in 0..127 && it.velocity in 1..127 && it.startTick >= 0 && it.endTick - it.startTick >= MelodyCohesionValidator.MIN_NOTE_TICKS }.sortedWith(noteOrder)
        if (valid.size != notes.size) audit += entry(occurrence, "remove", "${notes.size - valid.size} invalid note(s)")
        val collisionFree = if (repair) removeCollisions(valid, occurrence, audit) else valid
        require(collisionFree.all { it.pitch in 0..127 && it.endTick - it.startTick >= MelodyCohesionValidator.MIN_NOTE_TICKS }) { "Cohesion produced an invalid MIDI note" }
        require(!hasCollisions(collisionFree)) { "Cohesion produced colliding MIDI notes; add the bounded repair edit" }
        return MelodyTransformationResult(collisionFree, audit)
    }

    private fun removeCollisions(notes: List<MidiNote>, occurrence: MelodyOccurrencePlan, audit: MutableList<MelodyAuditEntry>): List<MidiNote> {
        val retained = mutableListOf<MidiNote>()
        notes.forEach { note ->
            val overlap = retained.indexOfFirst { it.channel == note.channel && it.pitch == note.pitch && note.startTick < it.endTick }
            if (overlap >= 0) audit += entry(occurrence, "remove", "colliding note at ${note.startTick}") else retained += note
        }
        return retained
    }

    private fun hasCollisions(notes: List<MidiNote>): Boolean = notes.groupBy { it.channel to it.pitch }.values.any { group ->
        group.sortedBy { it.startTick }.zipWithNext().any { (left, right) -> right.startTick < left.endTick }
    }

    private fun inRange(tick: Long, start: Long?, end: Long?) = tick >= (start ?: 0L) && tick < (end ?: Long.MAX_VALUE)
    private fun aligned(tick: Long, grid: Int?): Long = if (grid == null) tick else (tick.toDouble() / grid).roundToLong() * grid
    private fun entry(plan: MelodyOccurrencePlan, action: String, detail: String) = MelodyAuditEntry(plan.instanceId, action, plan.instanceId, detail, plan.rationale)
    private companion object { val noteOrder = compareBy<MidiNote> { it.startTick }.thenBy { it.channel }.thenBy { it.pitch }.thenBy { it.endTick } }
}

/** Local deterministic fallback. It intentionally proposes no musical edits. */
class DeterministicMelodyCohesionPlanner {
    fun plan(input: MelodyCohesionInput): MelodyCohesionPlan = MelodyCohesionPlan(
        inputHash = input.inputHash,
        model = CohesionModelIdentity.DETERMINISTIC,
        occurrences = input.occurrences.map { source -> MelodyOccurrencePlan(source.instanceId, source.partId, source.sourceHash, rationale = "Preserve the validated melody") }
    ).also { it.requireValid(input) }
}

/** Strict JSON-only local-model adapter; parsing and validation happen before any MIDI is touched. */
@OptIn(ExperimentalSerializationApi::class)
class LocalQwenMelodyCohesionPlanner(private val client: LocalQwenClient = LmStudioQwenClient(), private val model: CohesionModelIdentity) {
    fun plan(input: MelodyCohesionInput): MelodyCohesionPlan {
        val response = client.complete(SYSTEM_PROMPT, promptJson.encodeToString(input))
        val parsed = try { strictJson.decodeFromString(MelodyCohesionPlan.serializer(), response) } catch (error: Exception) {
            throw IllegalArgumentException("Qwen returned invalid melody-cohesion JSON: ${error.message}", error)
        }
        val plan = parsed.copy(model = model)
        plan.requireValid(input)
        return plan
    }
    private companion object {
        val strictJson = Json { ignoreUnknownKeys = false }
        val promptJson = Json { encodeDefaults = true; explicitNulls = false }
        const val SYSTEM_PROMPT = """
            Return JSON only for a version-1 MelodyCohesionPlan. Never return paths, commands, code, plugins, instruments,
            arbitrary notes outside a supplied occurrence range, or any fields other than version, inputHash, model, occurrences.
            Copy each supplied instanceId, partId, sourceHash, and inputHash exactly. Edits are only transpose, timing, boundary,
            remove_invalid_or_colliding, or patch. Transpose is non-zero and within plus or minus 12 semitones. Timing shift is at
            most 480 ticks, scale is 0.90 through 1.10, and quantizeTicks is 60, 120, 240, or 480. Boundary deltas are at most
            480 ticks. A patch has at most eight notes and remains within its known occurrence tick range. Use short musical
            rationales. The deterministic engine, not you, writes MIDI. Do not claim copyright clearance or artist imitation.
        """
    }
}

/** Atomic, project-confined publication of reviewable cohesion artifacts. */
@OptIn(ExperimentalSerializationApi::class)
object MelodyCohesionStore {
    const val DIRECTORY = "cohesion"
    const val DRAFT_FILE = "cohesion/cohesion.draft.json"
    const val APPROVED_FILE = "cohesion/cohesion.json"
    const val AUDIT_FILE = "cohesion/audit.json"
    const val PROVENANCE_FILE = "cohesion/provenance.json"
    private val json = Json { prettyPrint = true; encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = false }

    fun writeDraft(root: Path, input: MelodyCohesionInput, plan: MelodyCohesionPlan): Path {
        plan.requireValid(input)
        val text = json.encodeToString(plan)
        return atomicWrite(root.resolve(DRAFT_FILE), text).also {
            persistWorkflow(root, CohesionWorkflowReferences(
                inputSha256 = input.inputHash,
                plan = WorkflowArtifactReference(DRAFT_FILE, sha256(text)),
                occurrences = emptyList(),
                approved = false,
                structureSha256 = input.structureSha256
            ))
        }
    }

    fun readDraft(root: Path, input: MelodyCohesionInput): MelodyCohesionPlan = read(root.resolve(DRAFT_FILE), input)
    fun readApproved(root: Path, input: MelodyCohesionInput): MelodyCohesionPlan = read(root.resolve(APPROVED_FILE), input)

    /** Approval publishes new derived occurrence MIDI only after every result validates. */
    fun approve(root: Path, input: MelodyCohesionInput, sources: Map<String, List<MidiNote>>, commercial: Boolean = false, commerciallyApproved: (CohesionModelIdentity) -> Boolean = { it == CohesionModelIdentity.DETERMINISTIC }): Path {
        val plan = readDraft(root, input)
        require(!commercial || commerciallyApproved(plan.model)) { "Commercial output is blocked: selected cohesion model is not approved for commercial use" }
        val transformed = plan.occurrences.associateWith { occurrence -> MelodyCohesionTransformationEngine().transform(sources.getValue(occurrence.instanceId), occurrence) }
        val staging = root.resolve(DIRECTORY).resolve(".staging")
        Files.createDirectories(staging)
        try {
            transformed.forEach { (occurrence, result) -> writeMidi(staging.resolve("${occurrence.instanceId}.mid"), input.occurrences.first { it.instanceId == occurrence.instanceId }, result.notes) }
            transformed.keys.forEach { occurrence ->
                val target = root.resolve("$DIRECTORY/midi/${occurrence.instanceId}.mid")
                Files.createDirectories(target.parent)
                move(staging.resolve("${occurrence.instanceId}.mid"), target)
            }
            val audit = transformed.values.flatMap { it.audit }
            atomicWrite(root.resolve(AUDIT_FILE), json.encodeToString(audit))
            val planText = json.encodeToString(plan)
            atomicWrite(root.resolve(PROVENANCE_FILE), json.encodeToString(MelodyCohesionProvenance(plan.inputHash, sha256(planText), plan.model, approved = true)))
            return atomicWrite(root.resolve(APPROVED_FILE), planText).also {
                val references = transformed.keys.map { occurrence ->
                    val path = derivedMidi(root, occurrence.instanceId)
                    CohesionOccurrenceReference(
                        occurrence.instanceId,
                        occurrence.sourceHash,
                        WorkflowArtifactReference(root.relativize(path).toString().replace('\\', '/'), digest(path)),
                        approved = true
                    )
                }
                persistWorkflow(root, CohesionWorkflowReferences(
                    inputSha256 = input.inputHash,
                    plan = WorkflowArtifactReference(APPROVED_FILE, sha256(planText)),
                    occurrences = references,
                    approved = true,
                    structureSha256 = input.structureSha256
                ))
            }
        } finally { Files.list(staging).use { stream -> stream.forEach { Files.deleteIfExists(it) } } }
    }

    /** Rejecting a draft cannot modify an already approved cohesion artifact. */
    fun reject(root: Path, input: MelodyCohesionInput): Path {
        val plan = readDraft(root, input)
        val planText = json.encodeToString(plan)
        atomicWrite(root.resolve(PROVENANCE_FILE), json.encodeToString(MelodyCohesionProvenance(plan.inputHash, sha256(planText), plan.model, approved = false)))
        return atomicWrite(root.resolve("$DIRECTORY/rejected-${plan.inputHash}.json"), planText)
    }

    fun derivedMidi(root: Path, instanceId: String): Path {
        require(Regex("[A-Za-z0-9_-]{1,80}").matches(instanceId)) { "Invalid cohesion occurrence ID" }
        return root.resolve("$DIRECTORY/midi/$instanceId.mid")
    }

    private fun read(path: Path, input: MelodyCohesionInput): MelodyCohesionPlan {
        require(Files.isRegularFile(path)) { "Cohesion artifact not found: $path" }
        return json.decodeFromString(MelodyCohesionPlan.serializer(), Files.readString(path, StandardCharsets.UTF_8)).also { it.requireValid(input) }
    }

    private fun writeMidi(path: Path, input: MelodyOccurrenceInput, notes: List<MidiNote>) {
        val sequence = Sequence(Sequence.PPQ, input.ppq)
        val track = sequence.createTrack()
        input.tempoMap.forEach { tempo ->
            val micros = (60_000_000.0 / tempo.bpm).roundToLong().toInt()
            track.add(MidiEvent(MetaMessage(0x51, byteArrayOf((micros ushr 16).toByte(), (micros ushr 8).toByte(), micros.toByte()), 3), tempo.tick))
        }
        input.timeSignatures.forEach { signature -> track.add(MidiEvent(MetaMessage(0x58, byteArrayOf(signature.numerator.toByte(), log2(signature.denominator).toByte(), 24, 8), 4), signature.tick)) }
        notes.forEach { note ->
            track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, note.channel, note.pitch, note.velocity), note.startTick))
            track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, note.channel, note.pitch, 0), note.endTick))
        }
        track.add(MidiEvent(MetaMessage(0x2F, byteArrayOf(), 0), maxOf(input.durationTicks, notes.maxOfOrNull { it.endTick } ?: 0)))
        Files.createDirectories(checkNotNull(path.parent))
        val temporary = path.resolveSibling(".${path.fileName}.tmp")
        require(MidiSystem.write(sequence, 1, temporary.toFile()) > 0) { "Could not write derived cohesion MIDI" }
        // Reparse before publication: malformed output can never become canonical.
        MidiPartAnalyzer().analyze(temporary, input.partId)
        move(temporary, path)
    }

    private fun atomicWrite(path: Path, text: String): Path {
        Files.createDirectories(checkNotNull(path.parent))
        val temporary = path.resolveSibling(".${path.fileName}.tmp")
        Files.writeString(temporary, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        move(temporary, path)
        return path
    }
    private fun move(from: Path, to: Path) { try { Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) } catch (_: AtomicMoveNotSupportedException) { Files.move(from, to, StandardCopyOption.REPLACE_EXISTING) } }
    private fun log2(value: Int): Int { require(value > 0 && value and (value - 1) == 0) { "Invalid MIDI denominator" }; return Integer.numberOfTrailingZeros(value) }
    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
    private fun digest(path: Path): String = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }

    /** V3 records cohesion review atomically in project metadata; legacy files remain untouched. */
    private fun persistWorkflow(root: Path, cohesion: CohesionWorkflowReferences) {
        val project = runCatching { ProjectStore.read(root) }.getOrNull() ?: return
        if (project.version != Project.CURRENT_VERSION) return
        val workflow = project.workflow.invalidate(WorkflowChange.COHESION)
            .markCurrent(WorkflowArtifact.COHESION)
            .copy(cohesion = cohesion)
        ProjectStore.write(root, project.copy(workflow = workflow))
    }
}

/** Commercial projects must use an explicitly approved registry entry and exact model hash. */
fun ModelRegistry.approvesCommercialCohesion(model: CohesionModelIdentity): Boolean =
    getModel(ModelId(model.name, model.version))?.let { license ->
        license.isApprovedForCommercialUse() && license.modelHash == model.hash
    } == true

/** Builds path-free planner input and independently reads immutable selected MIDI. */
@OptIn(ExperimentalSerializationApi::class)
object MelodyCohesionInputFactory {
    private val fingerprintJson = Json { encodeDefaults = true; explicitNulls = false }
    /**
     * [requireCurrentAnalyses] is used only at the Structure-to-Cohesion
     * boundary. Other consumers reuse the immutable identity shape while
     * validating their own explicitly supplied analysis inputs.
     */
    fun build(
        root: Path,
        project: Project,
        planning: SongPlanningInput,
        requireCurrentAnalyses: Boolean = false
    ): Pair<MelodyCohesionInput, Map<String, List<MidiNote>>> {
        project.requireValid(root)
        val resolver = SelectedMidiArtifactResolver()
        val referencedPartIds = planning.structure.map(SectionInstance::partId).distinct()
        val selectedByPart = referencedPartIds.associateWith { partId ->
            val part = project.parts.singleOrNull { it.id == partId }
                ?: throw IllegalArgumentException("Structure references unknown part '$partId'.")
            resolver.resolve(root, project, part)
        }
        val notesByPart = selectedByPart.mapValues { (_, selected) -> readNotes(selected.path) }
        val analysisHashes = planning.analyses.mapValues { (partId, analysis) ->
            val part = project.parts.singleOrNull { it.id == partId }
                ?: throw IllegalArgumentException("Structure references unknown part '$partId'.")
            val reference = part.analysis
            if (reference == null) {
                require(!requireCurrentAnalyses) {
                    "Missing MIDI analysis for part '$partId'. Run part analyze first."
                }
                sha256(fingerprintJson.encodeToString(MidiAnalysis.serializer(), analysis).toByteArray(StandardCharsets.UTF_8))
            } else {
                require(reference.kind == AnalysisKind.MIDI) {
                    "MIDI analysis is required for part '$partId'. Run part analyze first."
                }
                val path = confinedAnalysis(root, reference.file, partId)
                val persisted = fingerprintJson.decodeFromString(MidiAnalysis.serializer(), Files.readString(path, StandardCharsets.UTF_8))
                require(persisted == analysis) { "MIDI analysis changed for part '$partId'. Run part analyze first." }
                if (requireCurrentAnalyses) {
                    val selected = selectedByPart.getValue(partId)
                    require(MidiPartAnalyzer().analyze(selected.path, partId) == analysis) {
                        "MIDI analysis is stale for the selected MIDI of part '$partId'. Run part analyze first."
                    }
                }
                sha256(Files.readAllBytes(path))
            }
        }
        val occurrences = planning.sectionsWithIdentity().map { occurrence ->
            val part = project.parts.first { it.id == occurrence.partId }
            val selected = selectedByPart.getValue(part.id)
            val path = selected.path
            val notes = notesByPart.getValue(part.id)
            val analysis = planning.analyses.getValue(part.id)
            MelodyOccurrenceInput(
                occurrence.instanceId, part.id, selected.sha256, analysis.ppq, analysis.durationTicks,
                analysis.pitchRange, analysis.key, analysis.tempoMap, analysis.timeSignatures, analysis.chords,
                analysis.energy,
                MelodyBoundarySummary(notes.any { it.startTick == 0L }, notes.any { it.endTick >= analysis.durationTicks }, notes.minOfOrNull { it.startTick }, notes.maxOfOrNull { it.endTick }),
                analysisHashes.getValue(part.id)
            )
        }
        val structureSha256 = sha256(fingerprintJson.encodeToString(planning.sectionsWithIdentity()).toByteArray(StandardCharsets.UTF_8))
        val boundaries = occurrences.zipWithNext { outgoing, incoming ->
            MelodyCohesionBoundaryInput(outgoing.instanceId, incoming.instanceId)
        }
        val withoutHash = MelodyCohesionInput(inputHash = "", structureSha256 = structureSha256, occurrences = occurrences, boundaries = boundaries)
        val input = withoutHash.copy(inputHash = sha256(fingerprintJson.encodeToString(withoutHash).toByteArray(StandardCharsets.UTF_8)))
        return input to input.occurrences.associate { it.instanceId to notesByPart.getValue(it.partId) }
    }

    private fun confinedAnalysis(root: Path, reference: String, partId: String): Path {
        val relative = runCatching { Path.of(reference) }.getOrElse {
            throw IllegalArgumentException("MIDI analysis path is invalid for part '$partId'.", it)
        }
        require(reference.isNotBlank() && !relative.isAbsolute && !reference.contains("..")) {
            "MIDI analysis path must be project-relative for part '$partId'."
        }
        val path = root.toAbsolutePath().normalize().resolve(relative).normalize()
        require(path.startsWith(root.toAbsolutePath().normalize()) && Files.isRegularFile(path)) {
            "MIDI analysis is missing for part '$partId'. Run part analyze first."
        }
        return path
    }

    private fun readNotes(path: Path): List<MidiNote> {
        val sequence = MidiSystem.getSequence(path.toFile())
        val events = sequence.tracks.flatMap { track -> (0 until track.size()).map(track::get) }.sortedBy { it.tick }
        val active = mutableMapOf<Pair<Int, Int>, ArrayDeque<Pair<Long, Int>>>()
        val result = mutableListOf<MidiNote>()
        events.forEach { event ->
            val message = event.message as? ShortMessage ?: return@forEach
            val key = message.channel to message.data1
            if (message.command == ShortMessage.NOTE_ON && message.data2 > 0) active.getOrPut(key) { ArrayDeque() }.addLast(event.tick to message.data2)
            else if (message.command == ShortMessage.NOTE_OFF || message.command == ShortMessage.NOTE_ON && message.data2 == 0) active[key]?.removeFirstOrNull()?.let { (start, velocity) -> result += MidiNote(message.channel, message.data1, velocity, start, event.tick) }
        }
        require(active.values.all { it.isEmpty() }) { "Selected MIDI has unclosed note events" }
        return result
    }
    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
