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
        val identity = MelodyIdentityBuilder.build(input, sequence.resolution * 4L / context.meterDenominator)
        val notes = locateNotes(sequence, context.correctedInputSha256)
        val inputNoteCount = notes.size
        require(notes.keys == context.notes.map { it.id }.toSet()) { "Enhancement note summary no longer matches corrected MIDI" }
        val byId = notes.mapValues { it.value.toImmutable() }
        plan.edits.forEach { edit -> validateEdit(edit, byId, context, policy, sequence.resolution) }
        val protected = identity.anchorIds.map(MelodyNoteId::value).toSet()
        val beforeAnchors = protected.associateWith { notes.getValue(it).pitch }
        val evidence = plan.edits.mapNotNull { edit -> notes[edit.noteId]?.let { note ->
            MidiMutation(
                operation = when (edit.kind) {
                    EnhancementEditKind.PITCH -> MidiMutationOperation.PITCH
                    EnhancementEditKind.TIMING -> MidiMutationOperation.TIMING
                    EnhancementEditKind.DURATION -> MidiMutationOperation.DURATION
                    EnhancementEditKind.VELOCITY -> MidiMutationOperation.VELOCITY
                    EnhancementEditKind.REMOVE_NOTE -> MidiMutationOperation.REMOVE
                    EnhancementEditKind.ADD_NOTE -> return@mapNotNull null
                },
                noteId = MelodyNoteId(edit.noteId),
                before = MidiMutationValues(note.channel, note.pitch, note.velocity, note.startTick, note.endTick),
                after = if (edit.kind == EnhancementEditKind.REMOVE_NOTE) null else MidiMutationValues(
                    note.channel,
                    if (edit.kind == EnhancementEditKind.PITCH) (note.pitch + edit.value).toInt() else note.pitch,
                    if (edit.kind == EnhancementEditKind.VELOCITY) (note.velocity + edit.value).toInt() else note.velocity,
                    if (edit.kind == EnhancementEditKind.TIMING) note.startTick + timingTicks(edit.value, context, sequence.resolution) else note.startTick,
                    when (edit.kind) {
                        EnhancementEditKind.TIMING -> note.endTick + timingTicks(edit.value, context, sequence.resolution)
                        EnhancementEditKind.DURATION -> note.startTick + edit.value
                        else -> note.endTick
                    },
                ),
                reasonCode = MidiMutationReasonCode.PHRASE_SHAPING
            )
        } }
        MidiMutationInvariants.requireAnchorPreservation(identity, evidence)
        plan.edits.filter { it.kind !in setOf(EnhancementEditKind.ADD_NOTE, EnhancementEditKind.REMOVE_NOTE) }
            .forEach { edit -> applyExisting(edit, notes.getValue(edit.noteId), context, sequence.resolution) }
        plan.edits.filter { it.kind == EnhancementEditKind.REMOVE_NOTE }.forEach { edit ->
            notes.getValue(edit.noteId).remove()
            notes.remove(edit.noteId)
        }
        plan.edits.filter { it.kind == EnhancementEditKind.ADD_NOTE }.forEach { edit ->
            val anchor = notes.getValue(requireNotNull(edit.anchorNoteId))
            add(edit, anchor)
        }
        val additions = plan.edits.count { it.kind == EnhancementEditKind.ADD_NOTE }
        val removals = plan.edits.count { it.kind == EnhancementEditKind.REMOVE_NOTE }
        val after = noteValues(sequence)
        require(after.size == byId.size - removals + additions && beforeAnchors.all { (id, pitch) -> notes[id]?.pitch == pitch }) {
            "Enhancement would alter recognizable melody anchors"
        }
        require(after.all { it.endTick > it.startTick }) { "Enhancement created an invalid duration" }
        require(after.none { it.pitch !in 0..127 || it.velocity !in 1..127 }) { "Enhancement created an invalid MIDI range" }
        requireNoCollisions(after)
        val distance = ((plan.edits.size * 100) / inputNoteCount.coerceAtLeast(1))
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
        when (edit.kind) {
            EnhancementEditKind.ADD_NOTE -> {
                val anchor = notes[edit.anchorNoteId] ?: throw IllegalArgumentException("Enhancement addition references an unknown anchor")
                require(edit.noteId !in notes && edit.pitch != null && edit.velocity != null && edit.startTick != null && edit.durationTicks != null && edit.channel != null)
                require(edit.pitch.mod(12) in context.scalePitchClasses) { "Enhancement addition violates the project scale" }
                require(edit.startTick >= notes.values.minOf(Note::startTick) && edit.startTick + edit.durationTicks <= notes.values.maxOf(Note::endTick)) {
                    "Enhancement addition escapes the recognizable melody range"
                }
                require(kotlin.math.abs(edit.pitch - anchor.pitch) <= 12) { "Enhancement addition is too far from its melodic anchor" }
            }
            EnhancementEditKind.REMOVE_NOTE -> require(notes.containsKey(edit.noteId)) { "Enhancement removal references an unknown note" }
            else -> {
                val note = notes[edit.noteId] ?: throw IllegalArgumentException("Enhancement edit references an unknown note")
                require(edit.pitch == null && edit.velocity == null && edit.startTick == null && edit.durationTicks == null && edit.channel == null && edit.anchorNoteId == null) {
                    "Existing-note enhancement contains addition-only fields"
                }
                when (edit.kind) {
                    EnhancementEditKind.VELOCITY -> require(note.velocity + edit.value in 1L..127L && kotlin.math.abs(edit.value) <= policy.maximumVelocityDelta)
                    EnhancementEditKind.PITCH -> require(note.pitch + edit.value in 0L..127L && kotlin.math.abs(edit.value) <= 2 &&
                        (note.pitch + edit.value).toInt().mod(12) in context.scalePitchClasses) { "Enhancement pitch edit violates range or project scale" }
                    EnhancementEditKind.TIMING -> require(kotlin.math.abs(edit.value) <= policy.maximumTimingShiftMs && note.startTick + timingTicks(edit.value, context, ppq) >= 0) { "Enhancement timing edit violates bounds" }
                    EnhancementEditKind.DURATION -> require(edit.value in 1..(context.ppq * context.meterNumerator).toLong()) { "Enhancement duration edit violates bounds" }
                    else -> error("unreachable")
                }
            }
        }
    }
    private fun applyExisting(edit: EnhancementEdit, note: MutableNote, context: MusicalProcessingContext, ppq: Int) = when (edit.kind) {
        EnhancementEditKind.VELOCITY -> note.on.setMessage(note.on.command, note.on.channel, note.on.data1, (note.velocity + edit.value).toInt())
        EnhancementEditKind.PITCH -> {
            val pitch = (note.pitch + edit.value).toInt()
            note.on.setMessage(note.on.command, note.on.channel, pitch, note.on.data2)
            note.off.setMessage(note.off.command, note.off.channel, pitch, note.off.data2)
        }
        EnhancementEditKind.TIMING -> { val ticks = timingTicks(edit.value, context, ppq); note.onEvent.tick += ticks; note.offEvent.tick += ticks }
        EnhancementEditKind.DURATION -> note.offEvent.tick = note.startTick + edit.value
        EnhancementEditKind.ADD_NOTE, EnhancementEditKind.REMOVE_NOTE -> error("Addition/removal must use its dedicated applier")
    }
    private fun add(edit: EnhancementEdit, anchor: MutableNote) {
        val pitch = requireNotNull(edit.pitch); val velocity = requireNotNull(edit.velocity)
        val start = requireNotNull(edit.startTick); val end = start + requireNotNull(edit.durationTicks)
        val channel = requireNotNull(edit.channel)
        anchor.track.add(javax.sound.midi.MidiEvent(ShortMessage(ShortMessage.NOTE_ON, channel, pitch, velocity), start))
        anchor.track.add(javax.sound.midi.MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, channel, pitch, 0), end))
    }
    private fun timingTicks(milliseconds: Long, context: MusicalProcessingContext, ppq: Int): Long =
        (milliseconds * context.bpm * ppq / 60_000L)
    private fun locateNotes(sequence: javax.sound.midi.Sequence, sourceSha256: String): MutableMap<String, MutableNote> {
        val active = mutableMapOf<Triple<Int, Int, Int>, ArrayDeque<Triple<javax.sound.midi.MidiEvent, ShortMessage, Int>>>()
        val ordinal = mutableMapOf<Pair<Int, Int>, Int>()
        val result = linkedMapOf<String, MutableNote>()
        sequence.tracks.forEachIndexed { trackIndex, track -> (0 until track.size()).forEach { i ->
            val event = track[i]; val message = event.message as? ShortMessage ?: return@forEach; val key = Triple(trackIndex, message.channel, message.data1)
            if (message.command == ShortMessage.NOTE_ON && message.data2 > 0) {
                val ordinalKey = trackIndex to message.channel
                val noteOnOrdinal = ordinal.getOrDefault(ordinalKey, 0)
                ordinal[ordinalKey] = noteOnOrdinal + 1
                active.getOrPut(key) { ArrayDeque() }.addLast(Triple(event, message, noteOnOrdinal))
            }
            else if (message.command == ShortMessage.NOTE_OFF || (message.command == ShortMessage.NOTE_ON && message.data2 == 0)) {
                val start = active[key]?.removeFirstOrNull() ?: throw IllegalArgumentException("MIDI contains unmatched note-off")
                require(event.tick > start.first.tick) { "MIDI contains a non-positive note" }
                result[MelodyNoteId.derive(sourceSha256, trackIndex, message.channel, start.third, message.data1, start.first.tick, event.tick).value] = MutableNote(track, start.first, event, start.second, message)
            }
        } }
        require(active.values.all { it.isEmpty() }) { "MIDI contains unclosed notes" }
        return result
    }
    private fun noteValues(sequence: javax.sound.midi.Sequence): List<ImmutableNote> {
        val active = mutableMapOf<Pair<Int, Int>, ArrayDeque<Pair<Long, Int>>>()
        val notes = mutableListOf<ImmutableNote>()
        sequence.tracks.forEach { track -> (0 until track.size()).forEach { index ->
            val event = track[index]; val message = event.message as? ShortMessage ?: return@forEach
            val key = message.channel to message.data1
            if (message.command == ShortMessage.NOTE_ON && message.data2 > 0) active.getOrPut(key) { ArrayDeque() }.addLast(event.tick to message.data2)
            else if (message.command == ShortMessage.NOTE_OFF || (message.command == ShortMessage.NOTE_ON && message.data2 == 0)) {
                val start = active[key]?.removeFirstOrNull() ?: throw IllegalArgumentException("MIDI contains unmatched note-off")
                notes += ImmutableNote(message.channel, message.data1, start.second, start.first, event.tick)
            }
        } }
        require(active.values.all { it.isEmpty() }) { "MIDI contains unclosed notes" }
        return notes
    }
    private fun requireNoCollisions(notes: Collection<Note>) {
        notes.groupBy { it.channel to it.pitch }.values.forEach { samePitch ->
            samePitch.sortedBy(Note::startTick).zipWithNext().forEach { (left, right) ->
                require(left.endTick <= right.startTick) { "Enhancement created an overlapping same-pitch note" }
            }
        }
    }
    private fun hash(path: Path): String = Files.newInputStream(path).use { input ->
        val digest = MessageDigest.getInstance("SHA-256"); val bytes = ByteArray(DEFAULT_BUFFER_SIZE)
        generateSequence { input.read(bytes).takeIf { it > 0 } }.forEach { digest.update(bytes, 0, it) }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
    private fun hashPlan(plan: EnhancementPlan): String = MessageDigest.getInstance("SHA-256").digest(plan.toString().toByteArray()).joinToString("") { "%02x".format(it) }
    private interface Note { val channel: Int; val pitch: Int; val velocity: Int; val startTick: Long; val endTick: Long }
    private data class ImmutableNote(override val channel: Int, override val pitch: Int, override val velocity: Int, override val startTick: Long, override val endTick: Long) : Note
    private class MutableNote(val track: javax.sound.midi.Track, val onEvent: javax.sound.midi.MidiEvent, val offEvent: javax.sound.midi.MidiEvent, val on: ShortMessage, val off: ShortMessage) : Note {
        override val channel get() = on.channel
        override val pitch get() = on.data1; override val velocity get() = on.data2; override val startTick get() = onEvent.tick; override val endTick get() = offEvent.tick
        fun toImmutable() = ImmutableNote(channel, pitch, velocity, startTick, endTick)
        fun remove() { track.remove(onEvent); track.remove(offEvent) }
    }
}
