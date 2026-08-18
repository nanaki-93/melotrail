package app.melotrail.arrangement

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
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

/** Persisted, code-owned choices. Future profiles may add bounded tempo/intensity fields. */
@Serializable
enum class MidiFeelProfile(val id: String, val targetBpm: Int, val swingPercent: Int) {
    LOFI_80_SWING_V1("lofi-80-swing-v1", 80, 58)
}

@Serializable
enum class MidiAnalysisInput { @SerialName("REPAIRED") CURRENT, LOFI_FEEL }

@Serializable
data class MidiFeelReferences(
    val profile: MidiFeelProfile,
    val derived: String,
    val report: String
)

@Serializable
data class MidiFeelReport(
    val version: Int = CURRENT_VERSION,
    val partId: String,
    val profile: MidiFeelProfile,
    val inputSha256: String,
    val outputSha256: String,
    val previousTempoMap: List<MidiTempoChange>,
    val outputTempoBpm: Int,
    val movedNoteCount: Int,
    val maximumShiftTicks: Long,
    val collisionRepairs: Int,
    val warnings: List<String> = emptyList()
) {
    fun requireValid() {
        require(version == CURRENT_VERSION) { "Unsupported MIDI feel report version: $version" }
        require(MidiQualityReport.PART_ID.matches(partId)) { "Invalid MIDI feel report part ID: $partId" }
        require(inputSha256.matches(HASH) && outputSha256.matches(HASH)) { "MIDI feel report fingerprints are invalid" }
        require(outputTempoBpm == profile.targetBpm) { "MIDI feel report tempo does not match its profile" }
        require(movedNoteCount >= 0 && maximumShiftTicks >= 0 && collisionRepairs >= 0) { "MIDI feel report metrics are invalid" }
        require(previousTempoMap.isNotEmpty() && previousTempoMap.first().tick == 0L) { "MIDI feel report tempo map must start at zero" }
        require(warnings.size <= 8 && warnings.all { it.isNotBlank() && it.length <= 240 }) { "MIDI feel report warnings are invalid" }
    }

    companion object { const val CURRENT_VERSION = 1; private val HASH = Regex("[0-9a-f]{64}") }
}

data class MidiFeelResult(val report: MidiFeelReport)

/** Deterministic Standard-MIDI transform; it neither renders audio nor mutates its input. */
class MidiLoFiFeelTransformer {
    fun transform(input: Path, output: Path, partId: String, profile: MidiFeelProfile = MidiFeelProfile.LOFI_80_SWING_V1): MidiFeelResult {
        require(MidiQualityReport.PART_ID.matches(partId)) { "Invalid MIDI feel part ID: $partId" }
        val sequence = readSequence(input)
        val events = sequence.tracks.flatMapIndexed { track, midiTrack ->
            (0 until midiTrack.size()).map { index -> IndexedEvent(midiTrack[index], track, index) }
        }.sortedWith(compareBy<IndexedEvent> { it.event.tick }.thenBy { it.track }.thenBy { it.index })
        val notes = pairNotes(events, input)
        val previousTempo = tempoMap(events)
        val signatures = timeSignatures(events)
        val scheduled = schedule(notes, sequence.resolution, profile)
        val moved = scheduled.count { it.start != it.note.start }
        val maximumShift = scheduled.maxOfOrNull { it.start - it.note.start } ?: 0L
        val warnings = buildList {
            if (sequence.resolution % 2 != 0) add("PPQN ${sequence.resolution} cannot represent every exact eighth-note offbeat; only exactly represented positions were swung.")
            if (scheduled.any { it.start != it.desiredStart }) add("Some swing moves were bounded to preserve same-pitch note ordering.")
            if (scheduled.any { it.end != it.start + it.note.duration }) add("Some note ends were shortened to prevent new same-pitch collisions.")
        }
        publishSequence(sequence, scheduled, output, profile, notes.map { it.signature }, signatures)
        return MidiFeelResult(MidiFeelReport(
            partId = partId,
            profile = profile,
            inputSha256 = sha256(input),
            outputSha256 = sha256(output),
            previousTempoMap = previousTempo,
            outputTempoBpm = profile.targetBpm,
            movedNoteCount = moved,
            maximumShiftTicks = maximumShift,
            collisionRepairs = scheduled.count { it.start != it.desiredStart || it.end != it.start + it.note.duration },
            warnings = warnings
        ).also(MidiFeelReport::requireValid))
    }

    private fun readSequence(path: Path): Sequence {
        require(Files.isRegularFile(path) && Files.size(path) >= 14) { "Cleaned MIDI is missing or invalid: $path" }
        Files.newInputStream(path).use { require(it.readNBytes(4).decodeToString() == "MThd") { "Cleaned MIDI is missing or invalid: $path" } }
        val sequence = try { MidiSystem.getSequence(path.toFile()) } catch (error: Exception) { throw IllegalArgumentException("Invalid cleaned MIDI '$path': ${error.message}", error) }
        require(sequence.divisionType == Sequence.PPQ && sequence.resolution > 0) { "Lo-fi Feel supports PPQ MIDI only." }
        return sequence
    }

    private fun pairNotes(events: List<IndexedEvent>, path: Path): List<NotePair> {
        val active = mutableMapOf<Pair<Int, Int>, ArrayDeque<IndexedEvent>>()
        val pairs = mutableListOf<NotePair>()
        events.forEach { indexed ->
            val message = indexed.event.message as? ShortMessage ?: return@forEach
            val on = message.command == ShortMessage.NOTE_ON && message.data2 > 0
            val off = message.command == ShortMessage.NOTE_OFF || (message.command == ShortMessage.NOTE_ON && message.data2 == 0)
            if (on) active.getOrPut(message.channel to message.data1) { ArrayDeque() }.addLast(indexed)
            if (off) {
                val start = active[message.channel to message.data1]?.removeFirstOrNull()
                    ?: throw IllegalArgumentException("Invalid cleaned MIDI '$path': unmatched note-off at ${indexed.event.tick}")
                require(indexed.event.tick > start.event.tick) { "Invalid cleaned MIDI '$path': non-positive note duration" }
                pairs += NotePair(start, indexed, message.channel, message.data1, start.event.tick, indexed.event.tick)
            }
        }
        require(active.values.all { it.isEmpty() }) { "Invalid cleaned MIDI '$path': unclosed note-on event" }
        return pairs.sortedWith(compareBy<NotePair> { it.channel }.thenBy { it.pitch }.thenBy { it.start }.thenBy { it.startEvent.track }.thenBy { it.startEvent.index })
    }

    private fun schedule(notes: List<NotePair>, ppq: Int, profile: MidiFeelProfile): List<ScheduledNote> {
        val targetOffset = (ppq.toDouble() * profile.swingPercent / 100.0).roundToLong()
        return notes.groupBy { it.channel to it.pitch }.values.flatMap { group ->
            val ordered = group.sortedWith(compareBy<NotePair> { it.start }.thenBy { it.startEvent.track }.thenBy { it.startEvent.index })
            val starts = LongArray(ordered.size) { index -> desiredStart(ordered[index], ppq, targetOffset) }
            for (index in starts.lastIndex - 1 downTo 0) {
                starts[index] = minOf(starts[index], starts[index + 1] - 1L).coerceAtLeast(ordered[index].start)
            }
            ordered.mapIndexed { index, note ->
                var end = starts[index] + note.duration
                val nextStart = starts.getOrNull(index + 1)
                if (nextStart != null && note.end <= ordered[index + 1].start && end > nextStart) end = nextStart
                if (end <= starts[index]) { // no legal forward shift exists without a new collision
                    starts[index] = note.start
                    end = note.end
                }
                ScheduledNote(note, starts[index], end, desiredStart(note, ppq, targetOffset))
            }
        }.sortedWith(compareBy<ScheduledNote> { it.note.startEvent.track }.thenBy { it.note.startEvent.index })
    }

    private fun desiredStart(note: NotePair, ppq: Int, targetOffset: Long): Long {
        if ((note.start * 2L) % ppq != 0L || ((note.start * 2L) / ppq) % 2L != 1L) return note.start
        val quarterStart = (note.start / ppq) * ppq
        return (quarterStart + targetOffset).coerceAtLeast(note.start)
    }

    private fun publishSequence(source: Sequence, scheduled: List<ScheduledNote>, output: Path, profile: MidiFeelProfile, inputNotes: List<NoteSignature>, inputSignatures: List<MidiTimeSignature>) {
        val byEvent = scheduled.flatMap { listOf(it.note.startEvent to it.start, it.note.endEvent to it.end) }.toMap()
        val sequence = Sequence(Sequence.PPQ, source.resolution)
        source.tracks.forEachIndexed { trackIndex, track ->
            val destination = sequence.createTrack()
            (0 until track.size()).forEach { index ->
                val event = track[index]
                val meta = event.message as? MetaMessage
                if (meta?.type == 0x51) return@forEach
                destination.add(MidiEvent(event.message.clone() as javax.sound.midi.MidiMessage, byEvent[IndexedEvent(event, trackIndex, index)] ?: event.tick))
            }
        }
        val tempo = MetaMessage().apply { setMessage(0x51, byteArrayOf(0x0b, 0x71, 0xb0.toByte()), 3) } // 750000 µs/q = 80 BPM
        sequence.tracks.first().add(MidiEvent(tempo, 0L))
        Files.createDirectories(checkNotNull(output.parent))
        val temporary = output.resolveSibling(".${output.fileName}.tmp")
        try {
            require(MidiSystem.write(sequence, 1, temporary.toFile()) > 0) { "Could not write Lo-fi Feel MIDI" }
            validateOutput(temporary, source.resolution, profile, inputNotes, inputSignatures)
            try {
                Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (error: AtomicMoveNotSupportedException) {
                throw IllegalStateException("Atomic publication is not supported for Lo-fi Feel MIDI '$output'.", error)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun validateOutput(output: Path, ppq: Int, profile: MidiFeelProfile, inputNotes: List<NoteSignature>, inputSignatures: List<MidiTimeSignature>) {
        val sequence = readSequence(output)
        require(sequence.resolution == ppq) { "Lo-fi Feel output changed PPQN" }
        val events = sequence.tracks.flatMapIndexed { track, midiTrack -> (0 until midiTrack.size()).map { index -> IndexedEvent(midiTrack[index], track, index) } }
        val tempos = tempoMap(events)
        require(tempos.size == 1 && tempos.single().tick == 0L && tempos.single().bpm == profile.targetBpm.toDouble()) { "Lo-fi Feel output tempo must be exactly ${profile.targetBpm} BPM" }
        require(timeSignatures(events) == inputSignatures) { "Lo-fi Feel output changed time-signature events" }
        val outputNotes = pairNotes(events.sortedWith(compareBy<IndexedEvent> { it.event.tick }.thenBy { it.track }.thenBy { it.index }), output)
        require(outputNotes.map { it.signature }.sortedWith(compareBy<NoteSignature> { it.channel }.thenBy { it.pitch }.thenBy { it.velocity }) == inputNotes.sortedWith(compareBy<NoteSignature> { it.channel }.thenBy { it.pitch }.thenBy { it.velocity })) { "Lo-fi Feel output changed MIDI note identities" }
    }

    private fun tempoMap(events: List<IndexedEvent>): List<MidiTempoChange> {
        val tempos = events.mapNotNull { indexed ->
            val message = indexed.event.message as? MetaMessage ?: return@mapNotNull null
            if (message.type != 0x51 || message.data.size != 3) return@mapNotNull null
            val micros = ((message.data[0].toInt() and 0xff) shl 16) or ((message.data[1].toInt() and 0xff) shl 8) or (message.data[2].toInt() and 0xff)
            require(micros > 0) { "Invalid MIDI tempo" }; MidiTempoChange(indexed.event.tick, 60_000_000.0 / micros)
        }.sortedBy { it.tick }.distinctBy { it.tick }
        return if (tempos.firstOrNull()?.tick == 0L) tempos else listOf(MidiTempoChange(0, 120.0, true)) + tempos
    }

    private fun timeSignatures(events: List<IndexedEvent>): List<MidiTimeSignature> {
        val signatures = events.mapNotNull { indexed ->
            val message = indexed.event.message as? MetaMessage ?: return@mapNotNull null
            if (message.type != 0x58 || message.data.size < 2) return@mapNotNull null
            val numerator = message.data[0].toInt() and 0xff
            val exponent = message.data[1].toInt() and 0xff
            require(numerator > 0 && exponent in 0..5) { "Unsupported MIDI time signature" }
            MidiTimeSignature(indexed.event.tick, numerator, 1 shl exponent)
        }.sortedBy { it.tick }.distinctBy { it.tick }
        return if (signatures.firstOrNull()?.tick == 0L) signatures else listOf(MidiTimeSignature(0, 4, 4, true)) + signatures
    }

    private fun sha256(path: Path): String = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
    private data class IndexedEvent(val event: MidiEvent, val track: Int, val index: Int)
    private data class NotePair(val startEvent: IndexedEvent, val endEvent: IndexedEvent, val channel: Int, val pitch: Int, val start: Long, val end: Long) { val duration get() = end - start; val signature get() = NoteSignature(channel, pitch, (startEvent.event.message as ShortMessage).data2) }
    private data class NoteSignature(val channel: Int, val pitch: Int, val velocity: Int)
    private data class ScheduledNote(val note: NotePair, var start: Long, val end: Long, val desiredStart: Long)
}

/** Project-confined atomic report persistence and freshness checks for Lo-fi Feel. */
object MidiFeelReportStore {
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json { prettyPrint = true; encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = false }

    fun derivedPath(root: Path, partId: String, profile: MidiFeelProfile) = root.toAbsolutePath().normalize().resolve("midi/derived/$partId/${profile.id}.mid")
    fun reportPath(root: Path, partId: String, profile: MidiFeelProfile) = root.toAbsolutePath().normalize().resolve("midi/feel/$partId/${profile.id}.json")
    fun write(root: Path, report: MidiFeelReport): Path {
        report.requireValid(); val target = reportPath(root, report.partId, report.profile); Files.createDirectories(checkNotNull(target.parent)); val temporary = target.resolveSibling(".${target.fileName}.tmp")
        try { Files.writeString(temporary, json.encodeToString(report), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING); moveAtomically(temporary, target); return target } finally { Files.deleteIfExists(temporary) }
    }
    fun read(root: Path, reference: String): MidiFeelReport = try { json.decodeFromString(MidiFeelReport.serializer(), Files.readString(resolve(root, reference), StandardCharsets.UTF_8)).also(MidiFeelReport::requireValid) } catch (error: Exception) { throw IllegalArgumentException("MIDI feel report is malformed: $reference", error) }
    fun isCurrent(root: Path, partId: String, cleanReference: String, references: MidiFeelReferences): Boolean = runCatching {
        val report = read(root, references.report)
        report.partId == partId && report.profile == references.profile && report.inputSha256 == sha256(resolve(root, cleanReference)) && report.outputSha256 == sha256(resolve(root, references.derived))
    }.getOrDefault(false)
    fun requireCurrent(root: Path, partId: String, cleanReference: String, references: MidiFeelReferences) = require(isCurrent(root, partId, cleanReference, references)) { "Lo-fi Feel artifact is missing, malformed, or stale for part '$partId'. Choose Original feel or regenerate Lo-fi Feel." }
    private fun resolve(root: Path, reference: String): Path { val normalized = root.toAbsolutePath().normalize(); val relative = Path.of(reference); require(reference.isNotBlank() && !relative.isAbsolute) { "MIDI feel path must be project-relative" }; val path = normalized.resolve(relative).normalize(); require(path.startsWith(normalized) && Files.isRegularFile(path) && path.toRealPath().startsWith(normalized.toRealPath())) { "MIDI feel artifact is missing: $reference" }; return path }
    private fun sha256(path: Path) = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
    private fun moveAtomically(source: Path, target: Path) = try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) } catch (error: AtomicMoveNotSupportedException) { throw IllegalStateException("Atomic publish is not supported for MIDI feel report '$target'", error) }
}
