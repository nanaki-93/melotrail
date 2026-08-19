package app.melotrail.arrangement

import kotlinx.serialization.Serializable
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import javax.sound.midi.MetaMessage
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiMessage
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage
import kotlin.math.abs
import kotlin.math.roundToLong

/** The closed set of rendered MIDI jobs. It is intentionally independent of local instruments. */
@Serializable
enum class HumanizationRole { PIANO, BASS, DRUMS, PAD, STRINGS, TRANSITIONS }

/**
 * Fully resolved, persisted processor input. Values are deliberately small so
 * this stage can add feel without becoming a second composition engine.
 */
@Serializable
data class HumanizationConfig(
    val amountPercent: Int = 50,
    val timingMaxMs: Int = 12,
    val velocityMaxDelta: Int = 8,
    val durationMaxMs: Int = 8,
    val chordStaggerMs: Int = 6,
    val swingPercent: Int = 50,
    val drumTimingPercent: Int = 70,
    val bassTimingPercent: Int = 80
) {
    fun requireValid() {
        require(amountPercent in 0..100 && timingMaxMs in 0..80 && velocityMaxDelta in 0..32 && durationMaxMs in 0..80 && chordStaggerMs in 0..40) {
            "Humanization bounds are invalid"
        }
        require(swingPercent in 50..75 && drumTimingPercent in 0..100 && bassTimingPercent in 0..100) {
            "Humanization groove bounds are invalid"
        }
    }

    fun scaled(role: HumanizationRole, legacyGrooveApplied: Boolean): HumanizationConfig {
        requireValid()
        val rolePercent = when (role) {
            HumanizationRole.DRUMS -> drumTimingPercent
            HumanizationRole.BASS -> bassTimingPercent
            else -> 100
        }
        fun scale(value: Int) = (value.toLong() * amountPercent * rolePercent / 10_000L).toInt()
        return copy(
            timingMaxMs = scale(timingMaxMs),
            velocityMaxDelta = (velocityMaxDelta.toLong() * amountPercent / 100L).toInt(),
            durationMaxMs = scale(durationMaxMs),
            chordStaggerMs = scale(chordStaggerMs),
            // Legacy Lo-fi Feel already swung the selected part. A second swing is forbidden.
            swingPercent = if (legacyGrooveApplied) 50 else swingPercent
        )
    }
}

@Serializable
data class HumanizationEdit(
    val noteId: String,
    val channel: Int,
    val pitch: Int,
    val originalStartTick: Long,
    val originalEndTick: Long,
    val originalVelocity: Int,
    val startTick: Long,
    val endTick: Long,
    val velocity: Int,
    val reasons: List<String>
) {
    init {
        require(HUMANIZATION_ID.matches(noteId) && channel in 0..15 && pitch in 0..127 && originalVelocity in 1..127 && velocity in 1..127) {
            "Humanization edit identity is invalid"
        }
        require(originalStartTick >= 0 && originalEndTick > originalStartTick && startTick >= 0 && endTick > startTick) {
            "Humanization edit timing is invalid"
        }
        require(reasons.isNotEmpty() && reasons.size <= 4 && reasons.all { it in HUMANIZATION_REASONS }) {
            "Humanization edit reasons are invalid"
        }
    }
}

@Serializable
data class HumanizationRoleSummary(
    val role: HumanizationRole,
    val noteCount: Int,
    val changedNotes: Int,
    val maximumTimingShiftTicks: Long,
    val maximumVelocityDelta: Int,
    val maximumDurationDeltaTicks: Long,
    val collisionRepairs: Int
) {
    init {
        require(noteCount >= 0 && changedNotes in 0..noteCount && maximumTimingShiftTicks >= 0 && maximumVelocityDelta >= 0 && maximumDurationDeltaTicks >= 0 && collisionRepairs >= 0) {
            "Humanization role summary is invalid"
        }
    }
}

@Serializable
data class HumanizationReport(
    val version: Int = VERSION,
    val processorVersion: String = PROCESSOR_VERSION,
    val role: HumanizationRole,
    val inputSha256: String,
    val outputSha256: String,
    val seed: Long,
    val config: HumanizationConfig,
    val legacyGrooveApplied: Boolean,
    val edits: List<HumanizationEdit>,
    val summary: HumanizationRoleSummary,
    val warnings: List<String> = emptyList()
) {
    fun requireValid() {
        require(version == VERSION && processorVersion == PROCESSOR_VERSION && HUMANIZATION_HASH.matches(inputSha256) && HUMANIZATION_HASH.matches(outputSha256)) {
            "Humanization report identity is invalid"
        }
        config.requireValid(); require(summary.role == role && edits.size == summary.changedNotes)
        require(edits.map(HumanizationEdit::noteId).distinct().size == edits.size && warnings.size <= 8 && warnings.all { it.length in 1..240 }) {
            "Humanization report details are invalid"
        }
    }
}

data class HumanizationResult(val report: HumanizationReport)

/**
 * Deterministic MIDI-only transform. It preserves notes, pitch, track layout,
 * tempo, meter and all non-note messages. It never writes its input.
 */
class SeededHumanizationProcessor {
    fun transform(
        input: Path,
        output: Path,
        role: HumanizationRole,
        config: HumanizationConfig,
        seed: Long,
        legacyGrooveApplied: Boolean = false
    ): HumanizationResult {
        config.requireValid()
        val source = read(input)
        val all = indexed(source)
        val notes = pair(all, input)
        val effective = config.scaled(role, legacyGrooveApplied)
        val scheduled = schedule(notes, source, effective, seed, role)
        publish(source, scheduled, output)
        val edits = scheduled.mapNotNull { scheduledNote ->
            if (scheduledNote.note.start == scheduledNote.start && scheduledNote.note.end == scheduledNote.end && scheduledNote.note.velocity == scheduledNote.velocity) null
            else HumanizationEdit(
                scheduledNote.note.id, scheduledNote.note.channel, scheduledNote.note.pitch,
                scheduledNote.note.start, scheduledNote.note.end, scheduledNote.note.velocity,
                scheduledNote.start, scheduledNote.end, scheduledNote.velocity, scheduledNote.reasons
            )
        }
        val summary = HumanizationRoleSummary(
            role, notes.size, edits.size,
            edits.maxOfOrNull { abs(it.startTick - it.originalStartTick) } ?: 0,
            edits.maxOfOrNull { abs(it.velocity - it.originalVelocity) } ?: 0,
            edits.maxOfOrNull { abs((it.endTick - it.startTick) - (it.originalEndTick - it.originalStartTick)) } ?: 0,
            scheduled.count { it.collisionRepair }
        )
        return HumanizationResult(HumanizationReport(
            role = role, inputSha256 = digest(input), outputSha256 = digest(output), seed = seed, config = config,
            legacyGrooveApplied = legacyGrooveApplied, edits = edits, summary = summary,
            warnings = buildList { if (legacyGrooveApplied && config.swingPercent > 50) add("Legacy Lo-fi Feel evidence suppressed additional swing.") }
        ).also(HumanizationReport::requireValid))
    }

    private fun schedule(notes: List<Note>, sequence: Sequence, config: HumanizationConfig, seed: Long, role: HumanizationRole): List<Scheduled> {
        val ticksPerMs = sequence.resolution * tempoAtZero(sequence) / 60_000.0
        val timingMax = (config.timingMaxMs * ticksPerMs).roundToLong()
        val durationMax = (config.durationMaxMs * ticksPerMs).roundToLong()
        val staggerMax = (config.chordStaggerMs * ticksPerMs).roundToLong()
        val barTicks = barTicks(sequence).coerceAtLeast(1L)
        val endAnchor = sequence.tickLength
        val random = SplitMix64(seed xor role.ordinal.toLong().shl(48))
        val preliminary = notes.map { note ->
            val reasons = mutableListOf<String>()
            val anchoredStart = note.start == 0L || note.start % barTicks == 0L
            val anchoredEnd = note.end == endAnchor || note.end % barTicks == 0L
            val swing = swingOffset(note.start, sequence.resolution, config.swingPercent)
            val randomTiming = random.signed(timingMax)
            val timing = if (anchoredStart) 0L else (swing + randomTiming).coerceIn(-timingMax, timingMax)
            if (timing != 0L) reasons += if (swing != 0L) "swing" else "timing"
            val duration = if (anchoredEnd) 0L else random.signed(durationMax)
            if (duration != 0L) reasons += "duration"
            val velocity = (note.velocity + random.signed(config.velocityMaxDelta.toLong()).toInt()).coerceIn(1, 127)
            if (velocity != note.velocity) reasons += "velocity"
            Candidate(note, (note.start + timing).coerceAtLeast(0L), (note.end + timing + duration).coerceAtLeast(note.start + timing + 1L), velocity, reasons, anchoredStart, anchoredEnd)
        }.toMutableList()

        // Stagger chords only when their common onset is not a protected anchor.
        preliminary.groupBy { it.note.start }.values.forEach { chord ->
            if (chord.size < 2 || chord.any(Candidate::anchoredStart)) return@forEach
            chord.sortedWith(compareBy<Candidate> { it.note.pitch }.thenBy { it.note.channel }).forEachIndexed { index, candidate ->
                val offset = (index.toLong() - (chord.size - 1L) / 2L) * staggerMax / maxOf(1, chord.size - 1)
                if (offset != 0L) {
                    candidate.start = (candidate.start + offset).coerceAtLeast(0L)
                    candidate.end = (candidate.end + offset).coerceAtLeast(candidate.start + 1L)
                    candidate.reasons += "chord-stagger"
                }
            }
        }

        // Per channel/pitch ordering is repaired without deleting or crossing notes.
        preliminary.groupBy { it.note.channel to it.note.pitch }.values.forEach { group ->
            val ordered = group.sortedWith(compareBy<Candidate> { it.note.start }.thenBy { it.note.startEvent.track }.thenBy { it.note.startEvent.index })
            ordered.forEachIndexed { index, candidate ->
                val previous = ordered.getOrNull(index - 1)
                if (previous != null && candidate.start <= previous.start) {
                    candidate.start = previous.start + 1L
                    candidate.end = maxOf(candidate.end, candidate.start + 1L)
                    candidate.collisionRepair = true; candidate.reasons += "collision-repair"
                }
                val nextOriginal = ordered.getOrNull(index + 1)?.note
                if (nextOriginal != null && candidate.note.end <= nextOriginal.start && candidate.end > nextOriginal.start) {
                    candidate.end = nextOriginal.start.coerceAtLeast(candidate.start + 1L)
                    candidate.collisionRepair = true; candidate.reasons += "collision-repair"
                }
                if (candidate.anchoredEnd) candidate.end = candidate.note.end
            }
        }
        return preliminary.map { candidate ->
            Scheduled(candidate.note, candidate.start, candidate.end, candidate.velocity, candidate.reasons.distinct(), candidate.collisionRepair)
        }
    }

    private fun publish(source: Sequence, scheduled: List<Scheduled>, output: Path) {
        val ticks = scheduled.flatMap { listOf(it.note.startEvent to it.start, it.note.endEvent to it.end) }.toMap()
        val velocities = scheduled.associate { it.note.startEvent to it.velocity }
        val result = Sequence(Sequence.PPQ, source.resolution)
        source.tracks.forEachIndexed { trackIndex, track ->
            val target = result.createTrack()
            (0 until track.size()).forEach { index ->
                val event = track[index]; val key = IndexedEvent(event, trackIndex, index)
                val message = event.message.copyWithVelocity(velocities[key])
                target.add(MidiEvent(message, ticks[key] ?: event.tick))
            }
        }
        Files.createDirectories(requireNotNull(output.parent))
        val temporary = output.resolveSibling(".${output.fileName}.tmp")
        try {
            require(MidiSystem.write(result, 1, temporary.toFile()) > 0) { "Could not write humanized MIDI" }
            validate(source, read(temporary))
            try { Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
            catch (error: AtomicMoveNotSupportedException) { throw IllegalStateException("Atomic publication is unavailable for humanized MIDI '$output'.", error) }
        } finally { Files.deleteIfExists(temporary) }
    }

    private fun validate(input: Sequence, output: Sequence) {
        require(input.divisionType == output.divisionType && input.resolution == output.resolution) { "Humanization changed MIDI timing format" }
        val original = pair(indexed(input), null).map { Triple(it.channel, it.pitch, it.velocity) }.sortedBy { it.toString() }
        val transformed = pair(indexed(output), null).map { Triple(it.channel, it.pitch, it.velocity) }.sortedBy { it.toString() }
        require(original.size == transformed.size && original.map { it.first to it.second } == transformed.map { it.first to it.second }) { "Humanization changed MIDI note count or pitch" }
        require(meta(input, 0x51) == meta(output, 0x51) && meta(input, 0x58) == meta(output, 0x58)) { "Humanization changed tempo or meter" }
    }

    private fun read(path: Path): Sequence {
        require(Files.isRegularFile(path) && Files.size(path) >= 14) { "MIDI is missing or invalid: $path" }
        Files.newInputStream(path).use { require(it.readNBytes(4).decodeToString() == "MThd") { "MIDI is missing or invalid: $path" } }
        return try { MidiSystem.getSequence(path.toFile()).also { require(it.divisionType == Sequence.PPQ && it.resolution > 0) { "Humanization supports PPQ MIDI only." } } }
        catch (error: Exception) { throw IllegalArgumentException("Invalid MIDI '$path'", error) }
    }

    private fun indexed(sequence: Sequence): List<IndexedEvent> = sequence.tracks.flatMapIndexed { track, midiTrack ->
        (0 until midiTrack.size()).map { IndexedEvent(midiTrack[it], track, it) }
    }.sortedWith(compareBy<IndexedEvent> { it.event.tick }.thenBy { it.track }.thenBy { it.index })

    private fun pair(events: List<IndexedEvent>, path: Path?): List<Note> {
        val active = mutableMapOf<Pair<Int, Int>, ArrayDeque<IndexedEvent>>(); val notes = mutableListOf<Note>()
        events.forEach { indexed ->
            val message = indexed.event.message as? ShortMessage ?: return@forEach
            val on = message.command == ShortMessage.NOTE_ON && message.data2 > 0
            val off = message.command == ShortMessage.NOTE_OFF || (message.command == ShortMessage.NOTE_ON && message.data2 == 0)
            if (on) active.getOrPut(message.channel to message.data1) { ArrayDeque() }.addLast(indexed)
            if (off) {
                val start = active[message.channel to message.data1]?.removeFirstOrNull()
                    ?: throw IllegalArgumentException("Invalid MIDI${path?.let { " '$it'" }.orEmpty()}: unmatched note-off")
                require(indexed.event.tick > start.event.tick) { "Invalid MIDI: non-positive note duration" }
                notes += Note("n-${start.track}-${start.index}", start, indexed, message.channel, message.data1, (start.event.message as ShortMessage).data2, start.event.tick, indexed.event.tick)
            }
        }
        require(active.values.all { it.isEmpty() }) { "Invalid MIDI: unclosed note-on event" }
        return notes
    }

    private fun tempoAtZero(sequence: Sequence): Double {
        val tempo = indexed(sequence).firstOrNull { (it.event.message as? MetaMessage)?.type == 0x51 && it.event.tick == 0L }?.event?.message as? MetaMessage
        val micros = tempo?.data?.takeIf { it.size == 3 }?.let { ((it[0].toInt() and 0xff) shl 16) or ((it[1].toInt() and 0xff) shl 8) or (it[2].toInt() and 0xff) }
        return micros?.let { 60_000_000.0 / it } ?: 120.0
    }
    private fun barTicks(sequence: Sequence): Long {
        val signature = indexed(sequence).firstOrNull { (it.event.message as? MetaMessage)?.type == 0x58 && it.event.tick == 0L }?.event?.message as? MetaMessage
        val numerator = signature?.data?.getOrNull(0)?.toInt()?.and(0xff) ?: 4
        val denominator = signature?.data?.getOrNull(1)?.toInt()?.and(0xff)?.let { 1 shl it } ?: 4
        return sequence.resolution.toLong() * 4L * numerator / denominator
    }
    private fun swingOffset(start: Long, ppq: Int, percent: Int): Long {
        if (percent == 50 || (start * 2) % ppq != 0L || ((start * 2) / ppq) % 2L != 1L) return 0
        val quarter = start / ppq * ppq
        return (quarter + (ppq.toDouble() * percent / 100.0).roundToLong() - start).coerceAtLeast(0L)
    }
    private fun meta(sequence: Sequence, type: Int): List<Pair<Long, List<Byte>>> = indexed(sequence).mapNotNull { event ->
        val message = event.event.message as? MetaMessage ?: return@mapNotNull null
        if (message.type == type) event.event.tick to message.data.toList() else null
    }
    private fun MidiMessage.copyWithVelocity(velocity: Int?): MidiMessage {
        if (velocity == null) return clone() as MidiMessage
        val short = this as? ShortMessage ?: return clone() as MidiMessage
        if (short.command != ShortMessage.NOTE_ON || short.data2 == 0) return clone() as MidiMessage
        return ShortMessage(short.command, short.channel, short.data1, velocity)
    }
    private fun digest(path: Path): String = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }

    private data class IndexedEvent(val event: MidiEvent, val track: Int, val index: Int)
    private data class Note(val id: String, val startEvent: IndexedEvent, val endEvent: IndexedEvent, val channel: Int, val pitch: Int, val velocity: Int, val start: Long, val end: Long)
    private data class Candidate(val note: Note, var start: Long, var end: Long, val velocity: Int, val reasons: MutableList<String>, val anchoredStart: Boolean, val anchoredEnd: Boolean, var collisionRepair: Boolean = false)
    private data class Scheduled(val note: Note, val start: Long, val end: Long, val velocity: Int, val reasons: List<String>, val collisionRepair: Boolean)
}

private class SplitMix64(seed: Long) {
    private var state = seed
    private fun nextLong(): Long { state += -7046029254386353131L; var value = state; value = (value xor (value ushr 30)) * -4658895280553007687L; value = (value xor (value ushr 27)) * -7723592293110705685L; return value xor (value ushr 31) }
    fun signed(maximum: Long): Long = if (maximum == 0L) 0L else Math.floorMod(nextLong(), maximum * 2 + 1) - maximum
}

private const val VERSION = 1
private const val PROCESSOR_VERSION = "seeded-humanization-v1"
private val HUMANIZATION_HASH = Regex("[0-9a-f]{64}")
private val HUMANIZATION_ID = Regex("n-[0-9]+-[0-9]+")
private val HUMANIZATION_REASONS = setOf("timing", "swing", "duration", "velocity", "chord-stagger", "collision-repair")
