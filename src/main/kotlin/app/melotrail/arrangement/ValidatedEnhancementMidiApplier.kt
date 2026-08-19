package app.melotrail.arrangement

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import javax.sound.midi.MidiSystem
import javax.sound.midi.ShortMessage

/** Applies only a fully validated plan. Nothing is published until every MIDI invariant passes. */
class ValidatedEnhancementMidiApplier : EnhancementPlanApplier {
    override fun apply(input: Path, output: Path?, context: MusicalProcessingContext, plan: EnhancementPlan): EnhancementEditReport {
        val destination = requireNotNull(output) { "Enhancement output destination is required" }
        val policy = EnhancementPolicy.forIntensity(context.intensity)
        plan.requireValid(context, policy)
        require(hash(input) == context.correctedInputSha256) { "Corrected MIDI changed before enhancement" }
        val sequence = MidiSystem.getSequence(input.toFile())
        val notes = locateNotes(sequence)
        require(notes.keys == context.notes.map { it.id }.toSet()) { "Enhancement note summary no longer matches corrected MIDI" }
        val byId = notes.mapValues { it.value.toImmutable() }
        plan.edits.forEach { edit -> validateEdit(edit, byId, context, policy, sequence.resolution) }
        val beforeAnchors = anchors(byId.values)
        plan.edits.forEach { edit -> apply(edit, notes.getValue(edit.noteId), context, sequence.resolution) }
        val after = locateNotes(sequence)
        require(after.size == notes.size && anchors(after.values.map { it.toImmutable() }) == beforeAnchors) {
            "Enhancement would alter recognizable melody anchors"
        }
        require(after.values.all { it.endTick > it.startTick }) { "Enhancement created an invalid duration" }
        require(after.values.none { it.pitch !in 0..127 || it.velocity !in 1..127 }) { "Enhancement created an invalid MIDI range" }
        val distance = ((plan.edits.size * 100) / notes.size.coerceAtLeast(1))
        require(distance <= policy.maximumIdentityDistancePercent) { "Enhancement exceeds identity-distance budget" }
        val temp = destination.resolveSibling(".${destination.fileName}.enhancement.tmp")
        try {
            Files.createDirectories(requireNotNull(destination.parent))
            MidiSystem.write(sequence, 1, temp.toFile())
            require(hash(temp).isNotBlank() && MidiSystem.getSequence(temp.toFile()).resolution == sequence.resolution) { "Enhanced MIDI validation failed" }
            try { Files.move(temp, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
            catch (error: AtomicMoveNotSupportedException) { throw IllegalStateException("Atomic publication is unavailable for enhanced MIDI", error) }
        } finally { Files.deleteIfExists(temp) }
        return EnhancementEditReport(
            subjectHash = plan.subjectHash, inputSha256 = context.correctedInputSha256, outputSha256 = hash(destination),
            contextSha256 = context.contextSha256, intensity = context.intensity, processorId = plan.processorId,
            processorVersion = plan.processorVersion, placeholder = false, model = plan.model, appliedEdits = plan.edits,
            acceptedPlanSha256 = hashPlan(plan), identityDistancePercent = distance, anchorsRetained = true,
            message = if (plan.edits.isEmpty()) "Validated model plan contained no bounded edits." else "Applied ${plan.edits.size} validated enhancement edits."
        ).also(EnhancementEditReport::requireValid)
    }

    private fun validateEdit(edit: EnhancementEdit, notes: Map<String, Note>, context: MusicalProcessingContext, policy: EnhancementPolicy, ppq: Int) {
        val note = notes[edit.noteId] ?: throw IllegalArgumentException("Enhancement edit references an unknown note")
        when (edit.kind) {
            EnhancementEditKind.VELOCITY -> require(note.velocity + edit.value in 1L..127L && kotlin.math.abs(edit.value) <= policy.maximumVelocityDelta)
            EnhancementEditKind.PITCH -> require(note.pitch + edit.value in 0L..127L && kotlin.math.abs(edit.value) <= 2 &&
                (note.pitch + edit.value).toInt() in context.scalePitchClasses) { "Enhancement pitch edit violates range or project scale" }
            EnhancementEditKind.TIMING -> require(kotlin.math.abs(edit.value) <= policy.maximumTimingShiftMs && note.startTick + timingTicks(edit.value, context, ppq) >= 0) { "Enhancement timing edit violates bounds" }
        }
    }
    private fun apply(edit: EnhancementEdit, note: MutableNote, context: MusicalProcessingContext, ppq: Int) = when (edit.kind) {
        EnhancementEditKind.VELOCITY -> note.on.setMessage(note.on.command, note.on.channel, note.on.data1, (note.velocity + edit.value).toInt())
        EnhancementEditKind.PITCH -> {
            val pitch = (note.pitch + edit.value).toInt()
            note.on.setMessage(note.on.command, note.on.channel, pitch, note.on.data2)
            note.off.setMessage(note.off.command, note.off.channel, pitch, note.off.data2)
        }
        EnhancementEditKind.TIMING -> { val ticks = timingTicks(edit.value, context, ppq); note.onEvent.tick += ticks; note.offEvent.tick += ticks }
    }
    private fun timingTicks(milliseconds: Long, context: MusicalProcessingContext, ppq: Int): Long =
        (milliseconds * context.bpm * ppq / 60_000L)
    private fun locateNotes(sequence: javax.sound.midi.Sequence): MutableMap<String, MutableNote> {
        val active = mutableMapOf<Pair<Int, Int>, ArrayDeque<Pair<javax.sound.midi.MidiEvent, ShortMessage>>>()
        val result = linkedMapOf<String, MutableNote>()
        sequence.tracks.forEach { track -> (0 until track.size()).forEach { i ->
            val event = track[i]; val message = event.message as? ShortMessage ?: return@forEach; val key = message.channel to message.data1
            if (message.command == ShortMessage.NOTE_ON && message.data2 > 0) active.getOrPut(key) { ArrayDeque() }.addLast(event to message)
            else if (message.command == ShortMessage.NOTE_OFF || (message.command == ShortMessage.NOTE_ON && message.data2 == 0)) {
                val start = active[key]?.removeFirstOrNull() ?: throw IllegalArgumentException("MIDI contains unmatched note-off")
                require(event.tick > start.first.tick) { "MIDI contains a non-positive note" }
                result["n-${result.size.toString().padStart(5, '0')}"] = MutableNote(start.first, event, start.second, message)
            }
        } }
        require(active.values.all { it.isEmpty() }) { "MIDI contains unclosed notes" }
        return result
    }
    private fun anchors(notes: Collection<Note>): Pair<Int, Int> {
        val ordered = notes.sortedBy { it.startTick }; return ordered.first().pitch to ordered.last().pitch
    }
    private fun hash(path: Path): String = Files.newInputStream(path).use { input ->
        val digest = MessageDigest.getInstance("SHA-256"); val bytes = ByteArray(DEFAULT_BUFFER_SIZE)
        generateSequence { input.read(bytes).takeIf { it > 0 } }.forEach { digest.update(bytes, 0, it) }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
    private fun hashPlan(plan: EnhancementPlan): String = MessageDigest.getInstance("SHA-256").digest(plan.toString().toByteArray()).joinToString("") { "%02x".format(it) }
    private interface Note { val pitch: Int; val velocity: Int; val startTick: Long; val endTick: Long }
    private data class ImmutableNote(override val pitch: Int, override val velocity: Int, override val startTick: Long, override val endTick: Long) : Note
    private class MutableNote(val onEvent: javax.sound.midi.MidiEvent, val offEvent: javax.sound.midi.MidiEvent, val on: ShortMessage, val off: ShortMessage) : Note {
        override val pitch get() = on.data1; override val velocity get() = on.data2; override val startTick get() = onEvent.tick; override val endTick get() = offEvent.tick
        fun toImmutable() = ImmutableNote(pitch, velocity, startTick, endTick)
    }
}
