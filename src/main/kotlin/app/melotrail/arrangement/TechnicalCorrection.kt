package app.melotrail.arrangement

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiMessage
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage

/**
 * A deliberately narrow, path-free contract for technical MIDI correction.
 * It is separate from the legacy AI-fix contract: no operation can add notes,
 * change project settings, or invent harmony/phrases.
 */
@Serializable
data class TechnicalCorrectionContext(
    val version: Int = VERSION,
    val partId: String,
    val inputSha256: String,
    val contextSha256: String,
    val ppq: Int,
    val tempoMap: List<MidiTempoChange>,
    val timeSignatures: List<MidiTimeSignature>,
    val projectKey: String? = null,
    val harmony: List<TechnicalCorrectionHarmony> = emptyList(),
    val notes: List<TechnicalCorrectionNote>
) {
    fun requireValid() {
        require(version == VERSION && CORRECTION_ID.matches(partId) && CORRECTION_HASH.matches(inputSha256) && CORRECTION_HASH.matches(contextSha256)) { "Technical-correction identity is invalid" }
        require(ppq in 24..9_600 && tempoMap.isNotEmpty() && timeSignatures.isNotEmpty() && notes.size <= MAX_NOTES) { "Technical-correction timing or note context is invalid" }
        require(notes.map(TechnicalCorrectionNote::id).distinct().size == notes.size) { "Technical-correction note IDs are not unique" }
        notes.forEach(TechnicalCorrectionNote::requireValid)
        harmony.forEach(TechnicalCorrectionHarmony::requireValid)
    }

    companion object { const val VERSION = 1; const val MAX_NOTES = 4_000 }
}

@Serializable
data class TechnicalCorrectionHarmony(val sectionId: String, val pitches: List<Int>) {
    fun requireValid() = require(CORRECTION_ID.matches(sectionId) && pitches.isNotEmpty() && pitches.distinct().size == pitches.size && pitches.all { it in 0..11 }) {
        "Technical-correction harmony context is invalid"
    }
}

@Serializable
data class TechnicalCorrectionNote(val id: String, val channel: Int, val pitch: Int, val velocity: Int, val startTick: Long, val endTick: Long) {
    fun requireValid() = require(CORRECTION_NOTE_ID.matches(id) && channel in 0..15 && pitch in 0..127 && velocity in 1..127 && startTick >= 0 && endTick > startTick) {
        "Technical-correction note is invalid"
    }
}

@Serializable
enum class TechnicalCorrectionReason { DUPLICATE, COLLISION, OUT_OF_RANGE, BROKEN_DURATION, BROKEN_VELOCITY, STRONGLY_UNSUPPORTED }

@Serializable
enum class TechnicalCorrectionEditKind { REMOVE, SET_PITCH, SET_DURATION, SET_VELOCITY }

/** One code-owned edit with a concrete reason and confidence; additions are intentionally absent. */
@Serializable
data class TechnicalCorrectionEdit(
    val kind: TechnicalCorrectionEditKind,
    val reason: TechnicalCorrectionReason,
    val confidence: Double,
    val noteId: String,
    val pitch: Int? = null,
    val durationTicks: Long? = null,
    val velocity: Int? = null
)

@Serializable
data class TechnicalCorrectionPlan(
    val version: Int = TechnicalCorrectionContext.VERSION,
    val partId: String,
    val inputSha256: String,
    val contextSha256: String,
    val edits: List<TechnicalCorrectionEdit>
) {
    fun requireValid(context: TechnicalCorrectionContext, configuration: TechnicalCorrectionConfiguration = TechnicalCorrectionConfiguration()) =
        TechnicalCorrectionPlanValidator.requireValid(this, context, configuration)
}

/** Fixed limits are code-owned. A future planner can only propose this exact plan shape. */
data class TechnicalCorrectionConfiguration(
    val minimumPitch: Int = 21,
    val maximumPitch: Int = 108,
    val maximumEdits: Int = 32,
    val autoApplyConfidence: Double = 0.95
) {
    init { require(minimumPitch in 0..127 && maximumPitch in minimumPitch..127 && maximumEdits in 1..128 && autoApplyConfidence in 0.0..1.0) }
}

fun interface TechnicalCorrectionPlanner { fun plan(context: TechnicalCorrectionContext): TechnicalCorrectionPlan }

object TechnicalCorrectionPlanValidator {
    fun requireValid(plan: TechnicalCorrectionPlan, context: TechnicalCorrectionContext, configuration: TechnicalCorrectionConfiguration) {
        context.requireValid()
        require(plan.version == TechnicalCorrectionContext.VERSION && plan.partId == context.partId && plan.inputSha256 == context.inputSha256 && plan.contextSha256 == context.contextSha256) {
            "Technical-correction plan identity is stale or invalid"
        }
        require(plan.edits.size <= configuration.maximumEdits) { "Technical-correction plan exceeds its edit limit" }
        val notes = context.notes.associateBy(TechnicalCorrectionNote::id)
        require(plan.edits.map(TechnicalCorrectionEdit::noteId).distinct().size == plan.edits.size) { "Technical-correction plan edits a note more than once" }
        plan.edits.forEach { edit ->
            require(edit.confidence.isFinite() && edit.confidence in 0.0..1.0) { "Technical-correction confidence is invalid" }
            val note = requireNotNull(notes[edit.noteId]) { "Technical-correction edit references an unknown note" }
            when (edit.kind) {
                TechnicalCorrectionEditKind.REMOVE -> {
                    require(edit.pitch == null && edit.durationTicks == null && edit.velocity == null) { "Technical-correction removal contains unsupported fields" }
                    require(edit.reason == TechnicalCorrectionReason.DUPLICATE || edit.reason == TechnicalCorrectionReason.STRONGLY_UNSUPPORTED) { "Technical correction may remove only a duplicate or strongly unsupported note" }
                    if (edit.reason == TechnicalCorrectionReason.DUPLICATE) require(context.notes.any { it.id != note.id && it.channel == note.channel && it.pitch == note.pitch && it.velocity == note.velocity && it.startTick == note.startTick && it.endTick == note.endTick }) { "Removal is not bound to a detected duplicate" }
                    if (edit.reason == TechnicalCorrectionReason.STRONGLY_UNSUPPORTED) require(context.harmony.isNotEmpty()) { "Unsupported-note removal requires structured harmony context" }
                }
                TechnicalCorrectionEditKind.SET_PITCH -> {
                    val pitch = requireNotNull(edit.pitch) { "Pitch correction requires pitch" }
                    require(edit.durationTicks == null && edit.velocity == null && edit.reason == TechnicalCorrectionReason.OUT_OF_RANGE) { "Pitch correction is not a permitted technical edit" }
                    require(note.pitch !in configuration.minimumPitch..configuration.maximumPitch && pitch in configuration.minimumPitch..configuration.maximumPitch) { "Pitch correction is not a bounded range repair" }
                }
                TechnicalCorrectionEditKind.SET_DURATION -> {
                    val duration = requireNotNull(edit.durationTicks) { "Duration correction requires duration" }
                    require(edit.pitch == null && edit.velocity == null && edit.reason == TechnicalCorrectionReason.COLLISION) { "Duration correction is not a permitted technical edit" }
                    require(duration in 1..(note.endTick - note.startTick) && context.notes.any { other -> other.id != note.id && other.channel == note.channel && other.pitch == note.pitch && other.startTick in (note.startTick + 1)..note.endTick && duration == other.startTick - note.startTick }) { "Duration correction is not bound to a collision" }
                }
                TechnicalCorrectionEditKind.SET_VELOCITY -> {
                    val velocity = requireNotNull(edit.velocity) { "Velocity correction requires velocity" }
                    require(edit.pitch == null && edit.durationTicks == null && edit.reason == TechnicalCorrectionReason.BROKEN_VELOCITY && velocity in 1..127) { "Velocity correction is not a permitted technical edit" }
                }
            }
        }
    }
}

/** Deterministic conservative rules. It intentionally produces no musical additions or harmony substitutions. */
class DeterministicTechnicalCorrectionPlanner(
    private val configuration: TechnicalCorrectionConfiguration = TechnicalCorrectionConfiguration()
) : TechnicalCorrectionPlanner {
    override fun plan(context: TechnicalCorrectionContext): TechnicalCorrectionPlan {
        context.requireValid()
        val edits = buildList {
            context.notes.groupBy { listOf(it.channel, it.pitch, it.velocity, it.startTick, it.endTick) }.values.forEach { duplicates ->
                duplicates.drop(1).forEach { add(TechnicalCorrectionEdit(TechnicalCorrectionEditKind.REMOVE, TechnicalCorrectionReason.DUPLICATE, 1.0, it.id)) }
            }
            context.notes.filter { it.pitch !in configuration.minimumPitch..configuration.maximumPitch }.forEach { note ->
                if (size < configuration.maximumEdits) add(TechnicalCorrectionEdit(TechnicalCorrectionEditKind.SET_PITCH, TechnicalCorrectionReason.OUT_OF_RANGE, 1.0, note.id, pitch = note.pitch.coerceIn(configuration.minimumPitch, configuration.maximumPitch)))
            }
            context.notes.groupBy { it.channel to it.pitch }.values.forEach { notes ->
                notes.sortedBy { it.startTick }.zipWithNext().forEach { (first, second) ->
                    if (first.endTick > second.startTick && size < configuration.maximumEdits && none { it.noteId == first.id || it.noteId == second.id }) {
                        add(TechnicalCorrectionEdit(TechnicalCorrectionEditKind.SET_DURATION, TechnicalCorrectionReason.COLLISION, 1.0, first.id, durationTicks = second.startTick - first.startTick))
                    }
                }
            }
        }.take(configuration.maximumEdits)
        return TechnicalCorrectionPlan(partId = context.partId, inputSha256 = context.inputSha256, contextSha256 = context.contextSha256, edits = edits)
            .also { it.requireValid(context, configuration) }
    }
}

@Serializable
data class TechnicalCorrectionReport(
    val version: Int = TechnicalCorrectionContext.VERSION,
    val partId: String,
    val inputSha256: String,
    val outputSha256: String,
    val contextSha256: String,
    val planSha256: String,
    val edits: List<TechnicalCorrectionEdit>,
    val warnings: List<String> = emptyList(),
    val approvalRequired: Boolean = false
)

/** Applies a validated plan without changing source MIDI or timing metadata. */
class TechnicalCorrectionProcessor(
    private val configuration: TechnicalCorrectionConfiguration = TechnicalCorrectionConfiguration()
) {
    fun correct(input: Path, output: Path, context: TechnicalCorrectionContext, plan: TechnicalCorrectionPlan): TechnicalCorrectionReport {
        plan.requireValid(context, configuration)
        val before = sha256(input)
        require(before == context.inputSha256) { "Technical-correction input changed before processing" }
        val sequence = read(input)
        require(sequence.resolution == context.ppq) { "Technical-correction PPQ no longer matches context" }
        val notes = collectNotes(sequence)
        require(notes.map { it.id } == context.notes.map { it.id }) { "Technical-correction MIDI no longer matches its context" }
        val edits = plan.edits.filter { it.confidence >= configuration.autoApplyConfidence }
        val skipped = plan.edits.size - edits.size
        val changes = edits.associateBy { it.noteId }
        val transformed = Sequence(Sequence.PPQ, sequence.resolution)
        sequence.tracks.forEachIndexed { trackIndex, track ->
            val destination = transformed.createTrack()
            (0 until track.size()).forEach { eventIndex ->
                val event = track[eventIndex]
                val note = notes.firstOrNull { it.on.track == trackIndex && it.on.index == eventIndex || it.off.track == trackIndex && it.off.index == eventIndex }
                val edit = note?.let { changes[it.id] }
                if (edit?.kind == TechnicalCorrectionEditKind.REMOVE) return@forEach
                val message = event.message
                if (note != null && edit != null && message is ShortMessage) {
                    val on = note.on.track == trackIndex && note.on.index == eventIndex
                    val pitch = edit.pitch ?: note.pitch
                    val velocity = if (on) edit.velocity ?: note.velocity else message.data2
                    val tick = if (on) event.tick else edit.durationTicks?.let { note.start + it } ?: event.tick
                    destination.add(MidiEvent(ShortMessage(message.command, message.channel, pitch, velocity), tick))
                } else destination.add(MidiEvent(message.clone() as MidiMessage, event.tick))
            }
        }
        require(collectNotes(transformed).all { it.end > it.start && it.pitch in 0..127 && it.velocity in 1..127 }) { "Technical correction produced invalid MIDI" }
        writeAtomically(transformed, output)
        require(sha256(input) == before) { "Technical correction changed its input" }
        val result = read(output)
        require(result.resolution == sequence.resolution && timing(result) == timing(sequence)) { "Technical correction changed timing metadata" }
        val outputSha = sha256(output)
        val planSha = digest(json.encodeToString(plan).toByteArray(StandardCharsets.UTF_8))
        return TechnicalCorrectionReport(partId = context.partId, inputSha256 = before, outputSha256 = outputSha, contextSha256 = context.contextSha256, planSha256 = planSha,
            edits = edits, warnings = if (skipped == 0) emptyList() else listOf("$skipped low-confidence technical correction(s) were left unchanged."), approvalRequired = skipped > 0)
    }

    private fun read(path: Path): Sequence = try { MidiSystem.getSequence(path.toFile()).also { require(it.divisionType == Sequence.PPQ && it.resolution > 0) } }
    catch (error: Exception) { throw IllegalArgumentException("Technical-correction MIDI is malformed", error) }

    private fun collectNotes(sequence: Sequence): List<Note> {
        val active = mutableMapOf<Pair<Int, Int>, ArrayDeque<Start>>()
        val result = mutableListOf<Note>()
        sequence.tracks.forEachIndexed { trackIndex, track -> (0 until track.size()).forEach { eventIndex ->
            val event = track[eventIndex]; val message = event.message as? ShortMessage ?: return@forEach
            val key = message.channel to message.data1
            if (message.command == ShortMessage.NOTE_ON && message.data2 > 0) active.getOrPut(key) { ArrayDeque() }.addLast(Start(EventRef(trackIndex, eventIndex), event.tick, message.data2))
            else if (message.command == ShortMessage.NOTE_OFF || message.command == ShortMessage.NOTE_ON && message.data2 == 0) {
                val start = active[key]?.removeFirstOrNull() ?: throw IllegalArgumentException("Technical-correction MIDI has unmatched note-off")
                require(event.tick > start.tick) { "Technical-correction MIDI has non-positive note duration" }
                result += Note("n-${result.size.toString().padStart(5, '0')}", start.ref, EventRef(trackIndex, eventIndex), message.channel, message.data1, start.velocity, start.tick, event.tick)
            }
        } }
        require(active.values.all { it.isEmpty() }) { "Technical-correction MIDI has unclosed notes" }
        return result
    }

    private fun writeAtomically(sequence: Sequence, output: Path) {
        Files.createDirectories(requireNotNull(output.parent)); val temporary = output.resolveSibling(".${output.fileName}.tmp")
        try {
            require(MidiSystem.write(sequence, 1, temporary.toFile()) > 0) { "Could not write corrected MIDI" }
            read(temporary); Files.move(temporary, output, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temporary) }
    }

    private fun timing(sequence: Sequence): List<Pair<Int, Long>> = sequence.tracks.flatMap { track -> (0 until track.size()).mapNotNull { index -> (track[index].message as? javax.sound.midi.MetaMessage)?.let { it.type to track[index].tick } } }.sortedBy { it.second }
    private data class EventRef(val track: Int, val index: Int)
    private data class Start(val ref: EventRef, val tick: Long, val velocity: Int)
    private data class Note(val id: String, val on: EventRef, val off: EventRef, val channel: Int, val pitch: Int, val velocity: Int, val start: Long, val end: Long)
}

object TechnicalCorrectionContextFactory {
    fun build(project: Project, partId: String, input: Path): TechnicalCorrectionContext {
        val analysis = MidiPartAnalyzer().analyze(input, partId)
        val notes = TechnicalCorrectionProcessor().run { collectForContext(input) }
        val harmony = project.envelope.harmony?.progressions.orEmpty().map { progression ->
            TechnicalCorrectionHarmony(progression.sectionType.value, progression.events.flatMap { event -> event.quality.intervals.map { interval -> (event.root.chromatic + interval) % 12 } }.distinct().sorted())
        }.filter { it.pitches.isNotEmpty() }
        val bare = TechnicalCorrectionContext(partId = partId, inputSha256 = sha256(input), contextSha256 = "0".repeat(64), ppq = analysis.ppq,
            tempoMap = analysis.tempoMap, timeSignatures = analysis.timeSignatures, projectKey = project.envelope.compositionSettings?.key?.displayName, harmony = harmony, notes = notes)
        return bare.copy(contextSha256 = digest(json.encodeToString(bare.copy(contextSha256 = "")).toByteArray(StandardCharsets.UTF_8))).also(TechnicalCorrectionContext::requireValid)
    }

    /* Keeps note identity exactly aligned with the processor without exposing file paths in the contract. */
    private fun TechnicalCorrectionProcessor.collectForContext(input: Path): List<TechnicalCorrectionNote> {
        val sequence = MidiSystem.getSequence(input.toFile()); val active = mutableMapOf<Pair<Int, Int>, ArrayDeque<Pair<Long, Int>>>(); val result = mutableListOf<TechnicalCorrectionNote>()
        sequence.tracks.forEach { track -> (0 until track.size()).forEach { index ->
            val event = track[index]; val message = event.message as? ShortMessage ?: return@forEach; val key = message.channel to message.data1
            if (message.command == ShortMessage.NOTE_ON && message.data2 > 0) active.getOrPut(key) { ArrayDeque() }.addLast(event.tick to message.data2)
            else if (message.command == ShortMessage.NOTE_OFF || message.command == ShortMessage.NOTE_ON && message.data2 == 0) {
                val start = active[key]?.removeFirstOrNull() ?: throw IllegalArgumentException("Technical-correction MIDI has unmatched note-off")
                require(event.tick > start.first) { "Technical-correction MIDI has non-positive note duration" }
                result += TechnicalCorrectionNote("n-${result.size.toString().padStart(5, '0')}", message.channel, message.data1, start.second, start.first, event.tick)
            }
        } }
        require(active.values.all { it.isEmpty() }) { "Technical-correction MIDI has unclosed notes" }; return result
    }
}

private val json = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = false }
private val CORRECTION_ID = Regex("[A-Za-z0-9_-]{1,80}")
private val CORRECTION_NOTE_ID = Regex("n-[0-9]{5}")
private val CORRECTION_HASH = Regex("[0-9a-f]{64}")
private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
