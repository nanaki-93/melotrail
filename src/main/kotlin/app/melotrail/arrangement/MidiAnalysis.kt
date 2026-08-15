package app.melotrail.arrangement

import kotlinx.serialization.Serializable
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import javax.sound.midi.MetaMessage
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage

/**
 * Versioned facts and conservative inferences for a clean MIDI part.  Unlike
 * [PartAnalysis], this never describes decoded audio.
 *
 * Density is notes/(quarter-note beats * 4); rhythmic density is unique note
 * onsets/(quarter-note beats * 2); energy is 60% mean velocity and 40% note
 * density.  All three are clamped to 0..1. Key/chord confidence below 0.55 /
 * 0.50 / 0.75 respectively is represented by a null symbol rather than a guess.
 */
@Serializable
data class MidiAnalysis(
    val version: Int = 1,
    val partId: String,
    val ppq: Int,
    val durationTicks: Long,
    val durationSeconds: Double,
    val tempoMap: List<MidiTempoChange>,
    val timeSignatures: List<MidiTimeSignature>,
    val bars: Int,
    val beats: Double,
    val noteCount: Int,
    val pitchRange: MidiIntRange? = null,
    val velocity: MidiDoubleRange? = null,
    val noteLengthBeats: MidiDoubleRange? = null,
    val noteDensity: Double,
    val rhythmicDensity: Double,
    val melodicRange: Int? = null,
    val energy: Double,
    val key: MidiKey? = null,
    val chords: List<MidiChord> = emptyList()
)

@Serializable data class MidiTempoChange(val tick: Long, val bpm: Double, val inferred: Boolean = false)
@Serializable data class MidiTimeSignature(val tick: Long, val numerator: Int, val denominator: Int, val inferred: Boolean = false)
@Serializable data class MidiIntRange(val min: Int, val max: Int)
@Serializable data class MidiDoubleRange(val min: Double, val max: Double, val mean: Double)
@Serializable data class MidiKey(val tonic: String, val mode: String, val confidence: Double)
@Serializable data class MidiChord(val startTick: Long, val endTick: Long, val symbol: String? = null, val confidence: Double)

data class MidiNote(val channel: Int, val pitch: Int, val velocity: Int, val startTick: Long, val endTick: Long)

/** Parses Standard MIDI files using the JDK MIDI reader; no model output is involved. */
class MidiPartAnalyzer {
    fun analyze(path: Path, partId: String): MidiAnalysis {
        require(PART_ID.matches(partId)) { "Invalid part ID for MIDI analysis: $partId" }
        require(Files.isRegularFile(path)) { "Clean MIDI file not found: $path" }
        val sequence = try {
            MidiSystem.getSequence(path.toFile())
        } catch (error: Exception) {
            throw IllegalArgumentException("Invalid MIDI file '$path': ${error.message ?: error.javaClass.simpleName}", error)
        }
        require(sequence.divisionType == Sequence.PPQ) { "Unsupported MIDI timing in '$path': only PPQ MIDI is supported" }
        require(sequence.resolution > 0) { "Invalid MIDI PPQ in '$path'" }

        val events = sequence.tracks.flatMapIndexed { trackIndex, track ->
            (0 until track.size()).map { eventIndex -> IndexedEvent(track[ eventIndex ], trackIndex, eventIndex) }
        }.sortedWith(compareBy<IndexedEvent> { it.event.tick }.thenBy { it.track }.thenBy { it.index })
        val tempos = tempoMap(events, sequence.resolution)
        val signatures = timeSignatures(events)
        val durationTicks = maxOf(sequence.tickLength, events.maxOfOrNull { it.event.tick } ?: 0L)
        val notes = notes(events, path)
        val windows = barWindows(durationTicks, signatures, sequence.resolution)
        val beats = windows.sumOf { (it.endTick - it.startTick).toDouble() / it.ticksPerBeat }
        val durations = notes.map { (it.endTick - it.startTick).toDouble() / sequence.resolution }
        val pitches = notes.map { it.pitch }
        val velocities = notes.map { it.velocity.toDouble() }
        val quarterBeats = durationTicks.toDouble() / sequence.resolution
        val noteDensity = normalized(notes.size.toDouble() / maxOf(quarterBeats * 4.0, 1.0))
        val rhythmicDensity = normalized(notes.map { it.startTick }.distinct().size / maxOf(quarterBeats * 2.0, 1.0))
        val meanVelocity = velocities.averageOrZero()

        return MidiAnalysis(
            partId = partId,
            ppq = sequence.resolution,
            durationTicks = durationTicks,
            durationSeconds = durationSeconds(durationTicks, tempos, sequence.resolution),
            tempoMap = tempos,
            timeSignatures = signatures,
            bars = windows.size,
            beats = finite(beats),
            noteCount = notes.size,
            pitchRange = pitches.rangeOrNull(),
            velocity = velocities.rangeOrNull(),
            noteLengthBeats = durations.rangeOrNull(),
            noteDensity = noteDensity,
            rhythmicDensity = rhythmicDensity,
            melodicRange = pitches.takeIf { it.isNotEmpty() }?.let { it.max() - it.min() },
            energy = normalized((meanVelocity / 127.0) * 0.6 + noteDensity * 0.4),
            key = inferKey(notes, sequence.resolution),
            chords = windows.map { inferChord(it, notes, sequence.resolution) }
        )
    }

    private fun notes(events: List<IndexedEvent>, path: Path): List<MidiNote> {
        val active = mutableMapOf<Pair<Int, Int>, ArrayDeque<MidiNoteStart>>()
        val complete = mutableListOf<MidiNote>()
        events.forEach { indexed ->
            val message = indexed.event.message as? ShortMessage ?: return@forEach
            val on = message.command == ShortMessage.NOTE_ON && message.data2 > 0
            val off = message.command == ShortMessage.NOTE_OFF || (message.command == ShortMessage.NOTE_ON && message.data2 == 0)
            if (!on && !off) return@forEach
            val key = message.channel to message.data1
            if (on) {
                active.getOrPut(key) { ArrayDeque() }.addLast(MidiNoteStart(indexed.event.tick, message.data2))
            } else {
                val start = active[key]?.removeFirstOrNull()
                    ?: throw IllegalArgumentException("Invalid MIDI '$path': note-off at tick ${indexed.event.tick} has no matching note-on (channel ${message.channel}, pitch ${message.data1})")
                require(indexed.event.tick > start.tick) {
                    "Invalid MIDI '$path': note at tick ${start.tick} has non-positive duration"
                }
                complete += MidiNote(message.channel, message.data1, start.velocity, start.tick, indexed.event.tick)
            }
        }
        val unclosed = active.entries.filter { it.value.isNotEmpty() }
        require(unclosed.isEmpty()) {
            "Invalid MIDI '$path': ${unclosed.sumOf { it.value.size }} unclosed note-on event(s)"
        }
        return complete.sortedWith(compareBy<MidiNote> { it.startTick }.thenBy { it.channel }.thenBy { it.pitch })
    }

    private fun tempoMap(events: List<IndexedEvent>, ppq: Int): List<MidiTempoChange> {
        val explicit = events.mapNotNull { indexed ->
            val message = indexed.event.message as? MetaMessage ?: return@mapNotNull null
            if (message.type != 0x51 || message.data.size != 3) return@mapNotNull null
            val micros = ((message.data[0].toInt() and 0xff) shl 16) or ((message.data[1].toInt() and 0xff) shl 8) or (message.data[2].toInt() and 0xff)
            require(micros > 0) { "Invalid MIDI tempo at tick ${indexed.event.tick}" }
            MidiTempoChange(indexed.event.tick, 60_000_000.0 / micros, false)
        }.sortedBy { it.tick }.fold(mutableListOf<MidiTempoChange>()) { result, tempo ->
            if (result.lastOrNull()?.tick == tempo.tick) result[result.lastIndex] = tempo else result += tempo
            result
        }
        return if (explicit.firstOrNull()?.tick == 0L) explicit else listOf(MidiTempoChange(0, 120.0, true)) + explicit
    }

    private fun timeSignatures(events: List<IndexedEvent>): List<MidiTimeSignature> {
        val explicit = events.mapNotNull { indexed ->
            val message = indexed.event.message as? MetaMessage ?: return@mapNotNull null
            if (message.type != 0x58 || message.data.size < 2) return@mapNotNull null
            val numerator = message.data[0].toInt() and 0xff
            val exponent = message.data[1].toInt() and 0xff
            require(numerator > 0 && exponent in 0..5) { "Unsupported MIDI time signature at tick ${indexed.event.tick}" }
            MidiTimeSignature(indexed.event.tick, numerator, 1 shl exponent, false)
        }.sortedBy { it.tick }.fold(mutableListOf<MidiTimeSignature>()) { result, signature ->
            if (result.lastOrNull()?.tick == signature.tick) result[result.lastIndex] = signature else result += signature
            result
        }
        return if (explicit.firstOrNull()?.tick == 0L) explicit else listOf(MidiTimeSignature(0, 4, 4, true)) + explicit
    }

    private fun durationSeconds(durationTicks: Long, tempos: List<MidiTempoChange>, ppq: Int): Double {
        var seconds = 0.0
        tempos.forEachIndexed { index, current ->
            if (current.tick >= durationTicks) return@forEachIndexed
            val end = minOf(durationTicks, tempos.getOrNull(index + 1)?.tick ?: durationTicks)
            seconds += (end - current.tick).toDouble() * 60.0 / (current.bpm * ppq)
        }
        return finite(seconds)
    }

    private fun barWindows(duration: Long, signatures: List<MidiTimeSignature>, ppq: Int): List<BarWindow> {
        if (duration == 0L) return emptyList()
        val windows = mutableListOf<BarWindow>()
        signatures.forEachIndexed { index, signature ->
            val segmentEnd = minOf(duration, signatures.getOrNull(index + 1)?.tick ?: duration)
            if (signature.tick >= duration) return@forEachIndexed
            val ticksPerBeat = ppq.toDouble() * 4.0 / signature.denominator
            require(ticksPerBeat % 1.0 == 0.0) { "Unsupported MIDI time signature ${signature.numerator}/${signature.denominator}: PPQ $ppq cannot represent its beat" }
            val barTicks = (ticksPerBeat * signature.numerator).toLong()
            if (index > 0) require((signature.tick - signatures[index - 1].tick) % previousBarTicks(signatures[index - 1], ppq) == 0L) {
                "Unsupported MIDI time-signature change at tick ${signature.tick}: changes must occur on a bar boundary"
            }
            var start = signature.tick
            while (start < segmentEnd) {
                windows += BarWindow(start, minOf(start + barTicks, segmentEnd), ticksPerBeat)
                start += barTicks
            }
        }
        return windows
    }

    private fun previousBarTicks(signature: MidiTimeSignature, ppq: Int): Long =
        (ppq.toDouble() * 4.0 / signature.denominator * signature.numerator).toLong()

    private fun inferKey(notes: List<MidiNote>, ppq: Int): MidiKey? {
        if (notes.isEmpty()) return null
        val evidence = DoubleArray(12)
        notes.forEach { note -> evidence[note.pitch % 12] += (note.endTick - note.startTick).toDouble() / ppq * (note.velocity / 127.0) }
        val candidates = KEY_PROFILES.flatMap { (mode, profile) -> (0..11).map { tonic -> KeyCandidate(tonic, mode, dot(evidence, profile, tonic)) } }
            .sortedWith(compareByDescending<KeyCandidate> { it.score }.thenBy { it.mode }.thenBy { it.tonic })
        val best = candidates.first()
        val second = candidates[1]
        val confidence = normalized(0.5 + 0.5 * ((best.score - second.score) / maxOf(best.score, 1e-9)))
        return if (confidence > KEY_THRESHOLD) MidiKey(PITCH_NAMES[best.tonic], best.mode, confidence) else null
    }

    private fun inferChord(window: BarWindow, notes: List<MidiNote>, ppq: Int): MidiChord {
        val weights = DoubleArray(12)
        notes.forEach { note ->
            val overlap = maxOf(0L, minOf(note.endTick, window.endTick) - maxOf(note.startTick, window.startTick))
            weights[note.pitch % 12] += overlap.toDouble() / ppq * (note.velocity / 127.0)
        }
        val total = weights.sum()
        if (total == 0.0) return MidiChord(window.startTick, window.endTick, null, 0.0)
        val candidates = (0..11).flatMap { root -> listOf(ChordCandidate(root, "major", triadScore(weights, root, intArrayOf(0, 4, 7))), ChordCandidate(root, "minor", triadScore(weights, root, intArrayOf(0, 3, 7)))) }
        val best = candidates.maxWith(compareBy<ChordCandidate> { it.score }.thenByDescending { it.mode == "minor" }.thenByDescending { -it.root })
        val distinctTones = intArrayOf(0, if (best.mode == "major") 4 else 3, 7).count { weights[(best.root + it) % 12] > 0.0 }
        val confidence = normalized(best.score / total)
        val symbol = if (distinctTones >= 3 && confidence >= CHORD_THRESHOLD) PITCH_NAMES[best.root] + if (best.mode == "minor") "m" else "" else null
        return MidiChord(window.startTick, window.endTick, symbol, confidence)
    }

    private fun triadScore(weights: DoubleArray, root: Int, intervals: IntArray): Double = intervals.sumOf { weights[(root + it) % 12] }
    private fun dot(evidence: DoubleArray, profile: DoubleArray, tonic: Int): Double = (0..11).sumOf { evidence[it] * profile[(it - tonic + 12) % 12] }
    private fun normalized(value: Double): Double = if (value.isFinite()) value.coerceIn(0.0, 1.0) else 0.0
    private fun finite(value: Double): Double = if (value.isFinite()) value else 0.0

    private data class IndexedEvent(val event: MidiEvent, val track: Int, val index: Int)
    private data class MidiNoteStart(val tick: Long, val velocity: Int)
    private data class BarWindow(val startTick: Long, val endTick: Long, val ticksPerBeat: Double)
    private data class KeyCandidate(val tonic: Int, val mode: String, val score: Double)
    private data class ChordCandidate(val root: Int, val mode: String, val score: Double)

    private companion object {
        val PART_ID = Regex("[A-Za-z0-9_-]+")
        const val KEY_THRESHOLD = 0.50
        const val CHORD_THRESHOLD = 0.75
        val PITCH_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        val KEY_PROFILES = listOf(
            "major" to doubleArrayOf(6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88),
            "minor" to doubleArrayOf(6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17)
        )
    }
}

/** Persists MIDI analysis atomically, then updates project.json only after it validates. */
object MidiAnalysisStore {
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json { prettyPrint = true; encodeDefaults = true; explicitNulls = false }

    fun write(projectRoot: Path, project: Project, partId: String, analysis: MidiAnalysis): Path {
        val root = projectRoot.toAbsolutePath().normalize()
        require(project.version == Project.CURRENT_VERSION) { "MIDI analysis requires a v2 project" }
        project.requireValid(root)
        require(project.parts.any { it.id == partId }) { "Part not found: $partId" }
        require(analysis.partId == partId) { "MIDI analysis part ID does not match: $partId" }
        val reference = "analysis/$partId.json"
        val target = root.resolve(reference)
        atomicWrite(target, json.encodeToString(analysis))
        val updated = project.copy(parts = project.parts.map { if (it.id == partId) it.copy(analysis = PartAnalysisReference(reference, AnalysisKind.MIDI)) else it })
        updated.requireValid(root)
        ProjectStore.write(root, updated)
        return target
    }

    private fun atomicWrite(path: Path, text: String) {
        Files.createDirectories(checkNotNull(path.parent))
        val temporary = path.resolveSibling(".${path.fileName}.tmp")
        Files.writeString(temporary, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        try { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) }
        catch (_: java.nio.file.AtomicMoveNotSupportedException) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING) }
    }
}

private fun List<Int>.rangeOrNull(): MidiIntRange? = takeIf { it.isNotEmpty() }?.let { MidiIntRange(it.min(), it.max()) }
private fun List<Double>.rangeOrNull(): MidiDoubleRange? = takeIf { it.isNotEmpty() }?.let { MidiDoubleRange(it.min(), it.max(), it.average()) }
private fun List<Double>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()
