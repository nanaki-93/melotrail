package app.melotrail.arrangement

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
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

/** Exact, bounded cleanup choices persisted with a MIDI-first part. */
@Serializable
data class MidiCleanupOptions(
    val requestVersion: Int = 2,
    val profile: MidiCleanupProfile = MidiCleanupProfile.CONSERVATIVE,
    val quantize: String? = null,
    val strength: Double = 0.0,
    val minNoteMs: Int = 50,
    val minVelocity: Int = 8,
    val normalizeVelocity: Boolean = false,
    val cleanSustain: Boolean = false
) {
    fun requireValid() {
        require(requestVersion == 2) { "Unsupported MIDI cleanup request version: $requestVersion" }
        require(minNoteMs in 1..1_000) { "MIDI cleanup minimum note length must be 1..1000 ms" }
        require(minVelocity in 0..127) { "MIDI cleanup minimum velocity must be 0..127" }
        require(strength in 0.0..1.0) { "MIDI cleanup strength must be 0.0..1.0" }
        val validGrid = quantize == null || quantize in VALID_GRIDS
        require(validGrid) { "MIDI cleanup quantize grid must be one of: ${VALID_GRIDS.joinToString()}" }
        if (profile == MidiCleanupProfile.TIGHTEN_TIMING) {
            require(quantize != null && strength > 0.0) { "tighten-timing requires a grid and strength greater than 0.0" }
        } else {
            require(quantize == null && strength == 0.0) { "Only tighten-timing may set quantization or strength" }
        }
        if (profile == MidiCleanupProfile.CONSERVATIVE) {
            require(!normalizeVelocity && !cleanSustain) { "conservative cleanup cannot normalize velocity or clean sustain" }
        }
    }

    companion object {
        private val VALID_GRIDS = setOf("1/4", "1/8", "1/16", "1/32")
    }
}

@Serializable
enum class MidiCleanupProfile { CONSERVATIVE, TRANSCRIPTION_SAFE, TIGHTEN_TIMING }

@Serializable
data class MidiQualityReport(
    val version: Int = CURRENT_VERSION,
    val partId: String,
    val raw: MidiQualityArtifact,
    val clean: MidiQualityArtifact,
    val cleanup: MidiCleanupOptions,
    val timing: MidiTimingChangeSummary,
    val tempoAndTimeSignaturesPreserved: Boolean,
    val warnings: List<MidiQualityWarning> = emptyList(),
    val recommendations: List<MidiQualityRecommendation> = emptyList()
) {
    fun requireValid() {
        require(version == CURRENT_VERSION) { "Unsupported MIDI quality report version: $version" }
        require(PART_ID.matches(partId)) { "Invalid MIDI quality report part ID: $partId" }
        cleanup.requireValid()
        raw.requireValid("raw")
        clean.requireValid("clean")
        timing.requireValid()
        require(warnings.size <= MAX_WARNINGS) { "MIDI quality report contains too many warnings" }
        require(recommendations.size <= MAX_RECOMMENDATIONS) { "MIDI quality report contains too many recommendations" }
        require(warnings.distinctBy(MidiQualityWarning::code).size == warnings.size) { "MIDI quality report warnings must be unique" }
        require(recommendations.distinct().size == recommendations.size) { "MIDI quality report recommendations must be unique" }
    }

    companion object {
        const val CURRENT_VERSION = 1
        internal val PART_ID = Regex("[A-Za-z0-9_-]+")
        const val MAX_WARNINGS = 8
        const val MAX_RECOMMENDATIONS = 3
    }
}

@Serializable
data class MidiQualityArtifact(
    val sha256: String,
    val noteCount: Int,
    val notesPerSecond: Double,
    val pitchRange: MidiIntRange? = null,
    val maximumPolyphony: Int,
    val durationTicks: Long,
    val durationSeconds: Double,
    val velocity: MidiVelocityDistribution? = null,
    val tempoMap: List<MidiTempoChange>,
    val timeSignatures: List<MidiTimeSignature>
) {
    fun requireValid(label: String) {
        require(sha256.matches(Regex("[0-9a-f]{64}"))) { "MIDI quality $label fingerprint is invalid" }
        require(noteCount >= 0 && maximumPolyphony >= 0 && durationTicks >= 0 && notesPerSecond.isFinite() && notesPerSecond >= 0.0) {
            "MIDI quality $label metrics are invalid"
        }
        require(durationSeconds.isFinite() && durationSeconds >= 0.0) { "MIDI quality $label duration is invalid" }
        pitchRange?.let { require(it.min in 0..127 && it.max in it.min..127) { "MIDI quality $label pitch range is invalid" } }
        velocity?.requireValid(label)
        require(tempoMap.isNotEmpty() && tempoMap.first().tick == 0L) { "MIDI quality $label tempo map must begin at tick 0" }
        require(timeSignatures.isNotEmpty() && timeSignatures.first().tick == 0L) { "MIDI quality $label time signature map must begin at tick 0" }
    }
}

@Serializable
data class MidiVelocityDistribution(val min: Int, val max: Int, val mean: Double, val median: Double) {
    fun requireValid(label: String) {
        require(min in 0..127 && max in min..127 && mean.isFinite() && median.isFinite() && mean in min.toDouble()..max.toDouble() && median in min.toDouble()..max.toDouble()) {
            "MIDI quality $label velocity distribution is invalid"
        }
    }
}

@Serializable
data class MidiTimingChangeSummary(
    val pairedNotes: Int,
    val removedNotes: Int,
    val addedNotes: Int,
    val changedStarts: Int,
    val changedEnds: Int,
    val maxStartShiftTicks: Long,
    val maxEndShiftTicks: Long
) {
    fun requireValid() = require(listOf(pairedNotes, removedNotes, addedNotes, changedStarts, changedEnds).all { it >= 0 } && maxStartShiftTicks >= 0 && maxEndShiftTicks >= 0) {
        "MIDI quality timing summary is invalid"
    }
}

@Serializable
data class MidiQualityWarning(val code: MidiQualityWarningCode, val message: String)

@Serializable
enum class MidiQualityWarningCode { EMPTY_CLEAN, HIGH_NOTE_RATE, HIGH_POLYPHONY, WIDE_PITCH_RANGE, LARGE_TIMING_SHIFT, TEMPO_OR_TIME_SIGNATURE_CHANGED }

@Serializable
enum class MidiQualityRecommendation { RETRY_TRANSCRIPTION, REVIEW_CLEANUP_PROFILE, REVIEW_TIMING }

/** Calculates deterministic facts only; it never edits MIDI or invokes a worker. */
class MidiQualityReporter {
    fun report(partId: String, rawPath: Path, cleanPath: Path, cleanup: MidiCleanupOptions): MidiQualityReport {
        require(MidiQualityReport.PART_ID.matches(partId)) { "Invalid MIDI quality report part ID: $partId" }
        cleanup.requireValid()
        val raw = inspect(rawPath)
        val clean = inspect(cleanPath)
        val timing = timing(raw.notes, clean.notes)
        val preserved = raw.tempoMap == clean.tempoMap && raw.timeSignatures == clean.timeSignatures
        val warnings = warnings(clean, timing, preserved)
        val recommendations = recommendations(clean, timing, warnings)
        return MidiQualityReport(
            partId = partId,
            raw = raw.artifact,
            clean = clean.artifact,
            cleanup = cleanup,
            timing = timing,
            tempoAndTimeSignaturesPreserved = preserved,
            warnings = warnings,
            recommendations = recommendations
        ).also(MidiQualityReport::requireValid)
    }

    private fun inspect(path: Path): InspectedMidi {
        require(Files.isRegularFile(path)) { "MIDI quality input is missing: $path" }
        require(Files.size(path) >= 14) { "MIDI quality input is not a MIDI file: $path" }
        Files.newInputStream(path).use { require(it.readNBytes(4).decodeToString() == "MThd") { "MIDI quality input is not a MIDI file: $path" } }
        val sequence = try { MidiSystem.getSequence(path.toFile()) } catch (error: Exception) {
            throw IllegalArgumentException("Invalid MIDI quality input '$path': ${error.message ?: error.javaClass.simpleName}", error)
        }
        require(sequence.divisionType == Sequence.PPQ && sequence.resolution > 0) { "MIDI quality input must use PPQ timing: $path" }
        val events = sequence.tracks.flatMapIndexed { track, midiTrack ->
            (0 until midiTrack.size()).map { index -> IndexedEvent(midiTrack[index], track, index) }
        }.sortedWith(compareBy<IndexedEvent> { it.event.tick }.thenBy { it.track }.thenBy { it.index })
        val notes = notes(events, path)
        val durationTicks = maxOf(sequence.tickLength, events.maxOfOrNull { it.event.tick } ?: 0L)
        val tempos = tempos(events)
        val signatures = signatures(events)
        val seconds = durationSeconds(durationTicks, tempos, sequence.resolution)
        val velocities = notes.map { it.velocity }.sorted()
        val artifact = MidiQualityArtifact(
            sha256 = sha256(path),
            noteCount = notes.size,
            notesPerSecond = if (seconds > 0.0) notes.size / seconds else 0.0,
            pitchRange = notes.map { it.pitch }.takeIf { it.isNotEmpty() }?.let { MidiIntRange(it.min(), it.max()) },
            maximumPolyphony = maximumPolyphony(notes),
            durationTicks = durationTicks,
            durationSeconds = seconds,
            velocity = velocities.takeIf { it.isNotEmpty() }?.let { MidiVelocityDistribution(it.first(), it.last(), it.average(), median(it)) },
            tempoMap = tempos,
            timeSignatures = signatures
        )
        return InspectedMidi(artifact, notes, tempos, signatures)
    }

    private fun notes(events: List<IndexedEvent>, path: Path): List<QualityNote> {
        val active = mutableMapOf<Pair<Int, Int>, ArrayDeque<NoteStart>>()
        val complete = mutableListOf<QualityNote>()
        events.forEach { indexed ->
            val message = indexed.event.message as? ShortMessage ?: return@forEach
            val on = message.command == ShortMessage.NOTE_ON && message.data2 > 0
            val off = message.command == ShortMessage.NOTE_OFF || (message.command == ShortMessage.NOTE_ON && message.data2 == 0)
            if (!on && !off) return@forEach
            val key = message.channel to message.data1
            if (on) active.getOrPut(key) { ArrayDeque() }.addLast(NoteStart(indexed.event.tick, message.data2))
            else {
                val start = active[key]?.removeFirstOrNull()
                    ?: throw IllegalArgumentException("Invalid MIDI quality input '$path': unmatched note-off at tick ${indexed.event.tick}")
                require(indexed.event.tick > start.tick) { "Invalid MIDI quality input '$path': non-positive note duration" }
                complete += QualityNote(message.channel, message.data1, start.velocity, start.tick, indexed.event.tick)
            }
        }
        require(active.values.all { it.isEmpty() }) { "Invalid MIDI quality input '$path': unclosed note-on event" }
        return complete.sortedWith(compareBy<QualityNote> { it.channel }.thenBy { it.pitch }.thenBy { it.startTick }.thenBy { it.endTick })
    }

    private fun tempos(events: List<IndexedEvent>): List<MidiTempoChange> {
        val explicit = events.mapNotNull { indexed ->
            val message = indexed.event.message as? MetaMessage ?: return@mapNotNull null
            if (message.type != 0x51 || message.data.size != 3) return@mapNotNull null
            val micros = ((message.data[0].toInt() and 0xff) shl 16) or ((message.data[1].toInt() and 0xff) shl 8) or (message.data[2].toInt() and 0xff)
            require(micros > 0) { "Invalid MIDI quality tempo at tick ${indexed.event.tick}" }
            MidiTempoChange(indexed.event.tick, 60_000_000.0 / micros)
        }.distinctBy { it.tick }
        return if (explicit.firstOrNull()?.tick == 0L) explicit else listOf(MidiTempoChange(0, 120.0, true)) + explicit
    }

    private fun signatures(events: List<IndexedEvent>): List<MidiTimeSignature> {
        val explicit = events.mapNotNull { indexed ->
            val message = indexed.event.message as? MetaMessage ?: return@mapNotNull null
            if (message.type != 0x58 || message.data.size < 2) return@mapNotNull null
            val numerator = message.data[0].toInt() and 0xff
            val exponent = message.data[1].toInt() and 0xff
            require(numerator > 0 && exponent in 0..5) { "Unsupported MIDI quality time signature at tick ${indexed.event.tick}" }
            MidiTimeSignature(indexed.event.tick, numerator, 1 shl exponent)
        }.distinctBy { it.tick }
        return if (explicit.firstOrNull()?.tick == 0L) explicit else listOf(MidiTimeSignature(0, 4, 4, true)) + explicit
    }

    private fun durationSeconds(duration: Long, tempos: List<MidiTempoChange>, ppq: Int): Double = tempos.mapIndexed { index, tempo ->
        val end = minOf(duration, tempos.getOrNull(index + 1)?.tick ?: duration)
        if (end <= tempo.tick) 0.0 else (end - tempo.tick).toDouble() * 60.0 / (tempo.bpm * ppq)
    }.sum()

    private fun maximumPolyphony(notes: List<QualityNote>): Int {
        var current = 0
        var maximum = 0
        notes.flatMap { listOf(PolyphonyEvent(it.startTick, 1), PolyphonyEvent(it.endTick, -1)) }
            .sortedWith(compareBy<PolyphonyEvent> { it.tick }.thenBy { it.delta })
            .forEach { event -> current += event.delta; maximum = maxOf(maximum, current) }
        return maximum
    }

    private fun timing(raw: List<QualityNote>, clean: List<QualityNote>): MidiTimingChangeSummary {
        val paired = minOf(raw.size, clean.size)
        var starts = 0; var ends = 0; var maxStart = 0L; var maxEnd = 0L
        raw.zip(clean).forEach { (before, after) ->
            val start = kotlin.math.abs(after.startTick - before.startTick)
            val end = kotlin.math.abs(after.endTick - before.endTick)
            if (start > 0) starts++
            if (end > 0) ends++
            maxStart = maxOf(maxStart, start)
            maxEnd = maxOf(maxEnd, end)
        }
        return MidiTimingChangeSummary(paired, (raw.size - paired).coerceAtLeast(0), (clean.size - paired).coerceAtLeast(0), starts, ends, maxStart, maxEnd)
    }

    private fun warnings(clean: InspectedMidi, timing: MidiTimingChangeSummary, preserved: Boolean): List<MidiQualityWarning> = buildList {
        if (clean.artifact.noteCount == 0) add(MidiQualityWarning(MidiQualityWarningCode.EMPTY_CLEAN, "Clean MIDI contains no notes."))
        if (clean.artifact.notesPerSecond > 24.0) add(MidiQualityWarning(MidiQualityWarningCode.HIGH_NOTE_RATE, "Clean MIDI exceeds 24 notes per second."))
        if (clean.artifact.maximumPolyphony > 12) add(MidiQualityWarning(MidiQualityWarningCode.HIGH_POLYPHONY, "Clean MIDI exceeds 12 simultaneous notes."))
        if ((clean.artifact.pitchRange?.let { it.max - it.min } ?: 0) > 72) add(MidiQualityWarning(MidiQualityWarningCode.WIDE_PITCH_RANGE, "Clean MIDI spans more than 72 semitones."))
        if (maxOf(timing.maxStartShiftTicks, timing.maxEndShiftTicks) > 240) add(MidiQualityWarning(MidiQualityWarningCode.LARGE_TIMING_SHIFT, "Cleanup shifted note timing by more than 240 ticks."))
        if (!preserved) add(MidiQualityWarning(MidiQualityWarningCode.TEMPO_OR_TIME_SIGNATURE_CHANGED, "Cleanup did not preserve the tempo or time-signature map."))
    }.take(MidiQualityReport.MAX_WARNINGS)

    private fun recommendations(clean: InspectedMidi, timing: MidiTimingChangeSummary, warnings: List<MidiQualityWarning>): List<MidiQualityRecommendation> = buildList {
        if (clean.artifact.noteCount == 0) add(MidiQualityRecommendation.RETRY_TRANSCRIPTION)
        if (warnings.any { it.code in setOf(MidiQualityWarningCode.HIGH_NOTE_RATE, MidiQualityWarningCode.HIGH_POLYPHONY, MidiQualityWarningCode.WIDE_PITCH_RANGE) }) add(MidiQualityRecommendation.REVIEW_CLEANUP_PROFILE)
        if (timing.changedStarts > 0 || timing.changedEnds > 0) add(MidiQualityRecommendation.REVIEW_TIMING)
    }.take(MidiQualityReport.MAX_RECOMMENDATIONS)

    private fun median(sorted: List<Int>): Double = if (sorted.size % 2 == 1) sorted[sorted.size / 2].toDouble() else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
    private fun sha256(path: Path): String = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
    private data class IndexedEvent(val event: MidiEvent, val track: Int, val index: Int)
    private data class NoteStart(val tick: Long, val velocity: Int)
    private data class QualityNote(val channel: Int, val pitch: Int, val velocity: Int, val startTick: Long, val endTick: Long)
    private data class PolyphonyEvent(val tick: Long, val delta: Int)
    private data class InspectedMidi(val artifact: MidiQualityArtifact, val notes: List<QualityNote>, val tempoMap: List<MidiTempoChange>, val timeSignatures: List<MidiTimeSignature>)
}

/** Project-confined report persistence and freshness validation. */
object MidiQualityReportStore {
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json { prettyPrint = true; encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = false }

    fun path(projectRoot: Path, partId: String): Path {
        require(MidiQualityReport.PART_ID.matches(partId)) { "Invalid MIDI quality report part ID: $partId" }
        return projectRoot.toAbsolutePath().normalize().resolve("midi/quality/$partId.json")
    }

    fun write(projectRoot: Path, report: MidiQualityReport): Path {
        report.requireValid()
        val target = path(projectRoot, report.partId)
        Files.createDirectories(checkNotNull(target.parent))
        val temporary = target.resolveSibling(".${target.fileName}.tmp")
        try {
            Files.writeString(temporary, json.encodeToString(report), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
            catch (error: AtomicMoveNotSupportedException) { throw IllegalStateException("Atomic publish is not supported for MIDI quality report '$target'", error) }
            return target
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    fun read(projectRoot: Path, reference: String): MidiQualityReport {
        val root = projectRoot.toAbsolutePath().normalize()
        val path = resolveReference(root, reference, "MIDI quality report")
        return try { json.decodeFromString(MidiQualityReport.serializer(), Files.readString(path, StandardCharsets.UTF_8)).also(MidiQualityReport::requireValid) }
        catch (error: Exception) { throw IllegalArgumentException("MIDI quality report is malformed: $reference", error) }
    }

    fun isCurrent(projectRoot: Path, partId: String, rawReference: String, cleanReference: String, cleanup: MidiCleanupOptions, reportReference: String): Boolean = runCatching {
        val root = projectRoot.toAbsolutePath().normalize()
        val report = read(root, reportReference)
        report.partId == partId && report.cleanup == cleanup &&
            report.raw.sha256 == fingerprint(resolveReference(root, rawReference, "Raw MIDI")) &&
            report.clean.sha256 == fingerprint(resolveReference(root, cleanReference, "Clean MIDI"))
    }.getOrDefault(false)

    fun requireCurrent(projectRoot: Path, partId: String, rawReference: String, cleanReference: String, cleanup: MidiCleanupOptions, reportReference: String) {
        require(isCurrent(projectRoot, partId, rawReference, cleanReference, cleanup, reportReference)) {
            "MIDI quality report is missing, malformed, or stale for part '$partId'; re-run MIDI cleanup before arrangement."
        }
    }

    private fun fingerprint(path: Path): String {
        require(Files.isRegularFile(path)) { "MIDI quality source is missing: $path" }
        return MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
    }

    private fun resolveReference(root: Path, reference: String, label: String): Path {
        val relative = try { Path.of(reference) } catch (error: Exception) {
            throw IllegalArgumentException("$label path is invalid", error)
        }
        require(reference.isNotBlank() && !relative.isAbsolute) { "$label path must be project-relative" }
        val path = root.resolve(relative).normalize()
        require(path.startsWith(root) && Files.isRegularFile(path)) { "$label is missing: $reference" }
        require(path.toRealPath().startsWith(root.toRealPath())) { "$label path escapes the project root" }
        return path
    }
}
