package app.melotrail.arrangement

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import javax.sound.midi.MetaMessage
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiMessage
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage
import app.melotrail.profile.CompositionProfileCatalog
import kotlin.math.roundToInt

/**
 * Bounded, non-creative policy for canonical MIDI.  A null tempo or meter
 * deliberately preserves the cleaned input map; this is the neutral policy
 * used until typed composition settings have been saved.
 */
@Serializable
data class MidiNormalizationConfig(
    val version: Int = CURRENT_VERSION,
    val targetPpq: Int = 960,
    val gridDenominator: Int = 32,
    val timingToleranceMs: Int = 0,
    val velocityMinimum: Int = 1,
    val velocityMaximum: Int = 127,
    val targetTempoBpm: Int? = null,
    val targetMeterNumerator: Int? = null,
    val targetMeterDenominator: Int? = null,
    val contextSha256: String? = null
) {
    fun requireValid() {
        require(version == CURRENT_VERSION) { "Unsupported MIDI normalization version: $version" }
        require(targetPpq in 96..9_600 && targetPpq % 8 == 0) { "MIDI normalization PPQ must be a multiple of 8 from 96 to 9600" }
        require(gridDenominator == 32) { "MIDI normalization supports only a conservative 1/32 grid" }
        require(timingToleranceMs in 0..80) { "MIDI normalization timing tolerance must be 0..80 ms" }
        require(velocityMinimum in 1..127 && velocityMaximum in velocityMinimum..127) { "MIDI normalization velocity range is invalid" }
        require((targetMeterNumerator == null) == (targetMeterDenominator == null)) { "MIDI normalization meter must be complete" }
        targetTempoBpm?.let { require(it in 30..240) { "MIDI normalization tempo must be 30..240 BPM" } }
        targetMeterNumerator?.let { numerator ->
            require(numerator in 1..12 && targetMeterDenominator in setOf(1, 2, 4, 8, 16)) { "MIDI normalization meter is invalid" }
        }
        contextSha256?.let { require(SHA256.matches(it)) { "MIDI normalization context fingerprint is invalid" } }
    }

    fun sha256(): String = sha256Hex(JSON.encodeToString(this))

    companion object {
        const val CURRENT_VERSION = 1
        private val SHA256 = Regex("[0-9a-f]{64}")
        private val JSON = Json { encodeDefaults = true }
    }
}

/** Resolves only persisted, validated setup through the typed catalog; incomplete setup stays neutral. */
object MidiNormalizationPolicy {
    fun resolve(project: Project, catalog: CompositionProfileCatalog): MidiNormalizationConfig {
        val settings = project.envelope.compositionSettings ?: return MidiNormalizationConfig()
        if (!settings.complete || settings.timeSignature.denominator !in setOf(1, 2, 4, 8, 16)) return MidiNormalizationConfig()
        val profile = catalog.resolve(requireNotNull(settings.profile), requireNotNull(settings.mood))
        val tolerance = profile.velocityTolerance
        return MidiNormalizationConfig(
            timingToleranceMs = profile.timingToleranceMs,
            velocityMinimum = (12 - tolerance / 4).coerceIn(1, 12),
            velocityMaximum = (120 + tolerance / 4).coerceIn(120, 127),
            targetTempoBpm = settings.tempo.bpm.roundToInt(),
            targetMeterNumerator = settings.timeSignature.numerator,
            targetMeterDenominator = settings.timeSignature.denominator,
            contextSha256 = profile.resolvedHash
        )
    }
}

@Serializable
data class MidiNormalizationArtifact(val sha256: String, val ppq: Int, val eventCount: Int, val noteCount: Int) {
    fun requireValid(label: String) {
        require(Regex("[0-9a-f]{64}").matches(sha256) && ppq > 0 && eventCount >= 0 && noteCount >= 0) {
            "MIDI normalization $label artifact is invalid"
        }
    }
}

/** Exact aggregate mutation accounting; pitches and note cardinality are invariants. */
@Serializable
data class MidiNormalizationChanges(
    val reorderedEvents: Int,
    val convertedTicks: Int,
    val gridAdjustedEvents: Int,
    val forcedPositiveDurations: Int,
    val velocityClamps: Int,
    val replacedTempoEvents: Int,
    val replacedMeterEvents: Int,
    val createdNotes: Int = 0,
    val deletedNotes: Int = 0,
    val changedPitches: Int = 0
) {
    fun requireValid() {
        listOf(reorderedEvents, convertedTicks, gridAdjustedEvents, forcedPositiveDurations, velocityClamps, replacedTempoEvents, replacedMeterEvents,
            createdNotes, deletedNotes, changedPitches).forEach { require(it >= 0) { "MIDI normalization change count is invalid" } }
        require(createdNotes == 0 && deletedNotes == 0 && changedPitches == 0) { "MIDI normalization must not create, delete, or change pitches" }
    }
}

@Serializable
data class MidiNormalizationWarning(val code: String, val message: String) {
    fun requireValid() = require(code in setOf("EXPRESSIVE_TIMING_PRESERVED", "DRUM_VELOCITY_PRESERVED")) { "MIDI normalization warning is invalid" }
}

@Serializable
data class MidiNormalizationReport(
    val version: Int = CURRENT_VERSION,
    val processorVersion: String = PROCESSOR_VERSION,
    val partId: String,
    val input: MidiNormalizationArtifact,
    val output: MidiNormalizationArtifact,
    val config: MidiNormalizationConfig,
    val configurationSha256: String,
    val changes: MidiNormalizationChanges,
    val warnings: List<MidiNormalizationWarning> = emptyList()
) {
    fun requireValid() {
        require(version == CURRENT_VERSION && processorVersion == PROCESSOR_VERSION && MidiQualityReport.PART_ID.matches(partId)) { "MIDI normalization report is invalid" }
        input.requireValid("input"); output.requireValid("output"); config.requireValid(); changes.requireValid()
        require(configurationSha256 == config.sha256()) { "MIDI normalization configuration fingerprint is stale" }
        require(warnings.size <= 4 && warnings.map(MidiNormalizationWarning::code).distinct().size == warnings.size) { "MIDI normalization warnings are invalid" }
        warnings.forEach(MidiNormalizationWarning::requireValid)
    }

    companion object {
        const val CURRENT_VERSION = 1
        const val PROCESSOR_VERSION = "1"
    }
}

/** JDK-MIDI implementation; worker cleanup remains the repair boundary. */
class MidiNormalizer {
    fun normalize(partId: String, input: Path, output: Path, config: MidiNormalizationConfig): MidiNormalizationReport {
        require(MidiQualityReport.PART_ID.matches(partId)) { "Invalid MIDI normalization part ID" }
        config.requireValid()
        val normalizedInput = input.toAbsolutePath().normalize()
        val normalizedOutput = output.toAbsolutePath().normalize()
        require(Files.isRegularFile(normalizedInput) && normalizedInput != normalizedOutput) { "MIDI normalization input/output is invalid" }
        val inputSha256 = sha256(normalizedInput)
        val before = read(normalizedInput)
        val sourcePpq = before.resolution
        val events = before.tracks.flatMapIndexed { trackIndex, track ->
            (0 until track.size()).map { eventIndex -> Event(trackIndex, eventIndex, track[eventIndex].tick, track[eventIndex].message.clone() as MidiMessage) }
        }.filterNot { it.message is MetaMessage && (it.message as MetaMessage).type == END_OF_TRACK }
        val inputNotes = notePitches(events)
        val tempo = tempoMap(events, sourcePpq, config.targetPpq)
        var converted = 0; var grid = 0; var forced = 0; var clamped = 0
        val normalized = events.map { event ->
            val tick = convert(event.tick, sourcePpq, config.targetPpq)
            if (tick != event.tick) converted++
            MutableEvent(event.track, event.index, tick, event.message)
        }.toMutableList()
        val active = mutableMapOf<Triple<Int, Int, Int>, ArrayDeque<MutableEvent>>()
        normalized.sortedWith(compareBy<MutableEvent> { it.track }.thenBy { it.tick }.thenBy { it.index }).forEach { event ->
            val short = event.message as? ShortMessage ?: return@forEach
            val key = Triple(event.track, short.channel, short.data1)
            if (short.command == ShortMessage.NOTE_ON && short.data2 > 0) {
                val adjusted = snap(event.tick, config.targetTempoBpm?.let { 60_000_000 / it } ?: tempoAt(tempo, event.tick), config)
                if (adjusted != event.tick) { event.tick = adjusted; grid++ }
                active.getOrPut(key) { ArrayDeque() }.addLast(event)
            } else if (short.command == ShortMessage.NOTE_OFF || (short.command == ShortMessage.NOTE_ON && short.data2 == 0)) {
                val adjusted = snap(event.tick, config.targetTempoBpm?.let { 60_000_000 / it } ?: tempoAt(tempo, event.tick), config)
                if (adjusted != event.tick) { event.tick = adjusted; grid++ }
                val start = active[key]?.removeFirstOrNull()
                if (start != null && event.tick <= start.tick) { event.tick = start.tick + 1; forced++ }
            }
            if (short.channel != DRUM_CHANNEL && short.command == ShortMessage.NOTE_ON && short.data2 > 0) {
                val velocity = short.data2.coerceIn(config.velocityMinimum, config.velocityMaximum)
                if (velocity != short.data2) { short.setMessage(ShortMessage.NOTE_ON, short.channel, short.data1, velocity); clamped++ }
            }
        }
        val replaceTempo = config.targetTempoBpm != null
        val replaceMeter = config.targetMeterNumerator != null
        val filtered = normalized.filterNot { event ->
            val meta = event.message as? MetaMessage
            (replaceTempo && meta?.type == TEMPO) || (replaceMeter && meta?.type == TIME_SIGNATURE)
        }.toMutableList()
        val replacedTempo = normalized.count { replaceTempo && (it.message as? MetaMessage)?.type == TEMPO }
        val replacedMeter = normalized.count { replaceMeter && (it.message as? MetaMessage)?.type == TIME_SIGNATURE }
        if (replaceTempo) filtered += MutableEvent(0, -2, 0, tempoMessage(requireNotNull(config.targetTempoBpm)))
        if (replaceMeter) filtered += MutableEvent(0, -1, 0, meterMessage(requireNotNull(config.targetMeterNumerator), requireNotNull(config.targetMeterDenominator)))
        val sequence = Sequence(Sequence.PPQ, config.targetPpq)
        val reordered = filtered.groupBy { it.track }.toSortedMap().values.sumOf { trackEvents ->
            val ordered = trackEvents.sortedWith(EVENT_ORDER)
            val original = trackEvents.sortedBy { it.index }
            val track = sequence.createTrack()
            ordered.forEach { track.add(MidiEvent(it.message, it.tick)) }
            ordered.zip(original).count { (a, b) -> a.index != b.index }
        }
        Files.createDirectories(requireNotNull(normalizedOutput.parent))
        MidiSystem.write(sequence, 1, normalizedOutput.toFile())
        require(sha256(normalizedInput) == inputSha256) { "MIDI normalization changed its input" }
        val after = read(normalizedOutput)
        val outputEvents = after.tracks.sumOf { it.size() - 1 }
        val outputNotes = notePitches(after.tracks.flatMapIndexed { t, track -> (0 until track.size()).map { i -> Event(t, i, track[i].tick, track[i].message) } })
        require(inputNotes == outputNotes) { "MIDI normalization changed note pitches or note count" }
        val warnings = buildList {
            if (config.timingToleranceMs == 0 && events.any { it.message is ShortMessage }) add(MidiNormalizationWarning("EXPRESSIVE_TIMING_PRESERVED", "Off-grid timing was preserved by the neutral timing policy."))
            if (events.any { (it.message as? ShortMessage)?.channel == DRUM_CHANNEL }) add(MidiNormalizationWarning("DRUM_VELOCITY_PRESERVED", "Drum-channel velocities were preserved."))
        }
        return MidiNormalizationReport(partId = partId,
            input = MidiNormalizationArtifact(inputSha256, sourcePpq, events.size, inputNotes.size),
            output = MidiNormalizationArtifact(sha256(normalizedOutput), after.resolution, outputEvents, outputNotes.size), config = config,
            configurationSha256 = config.sha256(),
            changes = MidiNormalizationChanges(reordered, converted, grid, forced, clamped, replacedTempo, replacedMeter), warnings = warnings).also(MidiNormalizationReport::requireValid)
    }

    private fun read(path: Path): Sequence = try {
        MidiSystem.getSequence(path.toFile()).also { require(it.divisionType == Sequence.PPQ && it.resolution > 0) { "MIDI normalization requires positive PPQ MIDI" } }
    } catch (error: Exception) { throw IllegalArgumentException("MIDI normalization input is malformed", error) }

    private fun convert(tick: Long, from: Int, to: Int): Long = BigDecimal.valueOf(tick).multiply(BigDecimal.valueOf(to.toLong()))
        .divide(BigDecimal.valueOf(from.toLong()), 0, RoundingMode.HALF_UP).longValueExact()

    private fun tempoMap(events: List<Event>, sourcePpq: Int, targetPpq: Int): List<Pair<Long, Int>> = events.mapNotNull { event ->
        (event.message as? MetaMessage)?.takeIf { it.type == TEMPO && it.data.size == 3 }?.let { meta ->
            val value = ((meta.data[0].toInt() and 255) shl 16) or ((meta.data[1].toInt() and 255) shl 8) or (meta.data[2].toInt() and 255)
            convert(event.tick, sourcePpq, targetPpq) to value
        }
    }.sortedBy { it.first }.ifEmpty { listOf(0L to 500_000) }

    private fun tempoAt(tempo: List<Pair<Long, Int>>, normalizedTick: Long): Int {
        return tempo.lastOrNull { it.first <= normalizedTick }?.second ?: 500_000
    }

    private fun snap(tick: Long, tempoMicros: Int, config: MidiNormalizationConfig): Long {
        if (config.timingToleranceMs == 0) return tick
        val grid = config.targetPpq / 8L
        val nearest = ((tick + grid / 2) / grid) * grid
        val tolerance = BigDecimal.valueOf(config.timingToleranceMs.toLong()).multiply(BigDecimal.valueOf(config.targetPpq.toLong()))
            .multiply(BigDecimal.valueOf(1_000L)).divide(BigDecimal.valueOf(tempoMicros.toLong()), 0, RoundingMode.HALF_UP).longValueExact()
        return if (kotlin.math.abs(nearest - tick) <= minOf(tolerance, grid / 4)) nearest else tick
    }

    private fun notePitches(events: List<Event>): List<Pair<Int, Int>> = events.mapNotNull { event ->
        val short = event.message as? ShortMessage
        if (short?.command == ShortMessage.NOTE_ON && short.data2 > 0) short.channel to short.data1 else null
    }.sortedWith(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second })

    private fun tempoMessage(bpm: Int): MetaMessage = MetaMessage().also { message ->
        val micros = 60_000_000 / bpm
        message.setMessage(TEMPO, byteArrayOf((micros shr 16).toByte(), (micros shr 8).toByte(), micros.toByte()), 3)
    }

    private fun meterMessage(numerator: Int, denominator: Int): MetaMessage = MetaMessage().also { message ->
        val exponent = Integer.numberOfTrailingZeros(denominator)
        message.setMessage(TIME_SIGNATURE, byteArrayOf(numerator.toByte(), exponent.toByte(), 24, 8), 4)
    }

    private data class Event(val track: Int, val index: Int, val tick: Long, val message: MidiMessage)
    private data class MutableEvent(val track: Int, val index: Int, var tick: Long, val message: MidiMessage)
    private companion object {
        const val TEMPO = 0x51; const val TIME_SIGNATURE = 0x58; const val END_OF_TRACK = 0x2f; const val DRUM_CHANNEL = 9
        val EVENT_ORDER = compareBy<MutableEvent> { it.tick }.thenBy {
            val message = it.message as? ShortMessage
            when {
                message == null -> 1
                message.command == ShortMessage.NOTE_OFF || (message.command == ShortMessage.NOTE_ON && message.data2 == 0) -> 0
                message.command == ShortMessage.NOTE_ON -> 3
                else -> 2
            }
        }.thenBy { it.index }
    }
}

/** Project-local report persistence. The report deliberately contains hashes, never UI paths. */
object MidiNormalizationReportStore {
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json { prettyPrint = true; encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = false }

    fun write(projectRoot: Path, partId: String, report: MidiNormalizationReport): Path {
        report.requireValid(); require(report.partId == partId) { "MIDI normalization report part does not match" }
        val root = projectRoot.toAbsolutePath().normalize()
        val target = root.resolve("midi/normalization/$partId.json")
        Files.createDirectories(checkNotNull(target.parent))
        val temp = target.resolveSibling(".${target.fileName}.tmp")
        try {
            Files.writeString(temp, json.encodeToString(report), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
            try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
            catch (error: AtomicMoveNotSupportedException) { throw IllegalStateException("Atomic publish is unavailable for MIDI normalization report", error) }
            return target
        } finally { Files.deleteIfExists(temp) }
    }

    fun read(projectRoot: Path, reference: String): MidiNormalizationReport = try {
        val root = projectRoot.toAbsolutePath().normalize(); val path = root.resolve(reference).normalize()
        require(path.startsWith(root) && Files.isRegularFile(path) && path.toRealPath().startsWith(root.toRealPath())) { "MIDI normalization report is missing" }
        json.decodeFromString<MidiNormalizationReport>(Files.readString(path)).also(MidiNormalizationReport::requireValid)
    } catch (error: Exception) { throw IllegalArgumentException("MIDI normalization report is malformed", error) }

    fun isCurrent(projectRoot: Path, partId: String, input: Path, output: Path, config: MidiNormalizationConfig, reference: String): Boolean = runCatching {
        val report = read(projectRoot, reference)
        val temporary = Files.createTempFile(output.parent, ".normalization-check-", ".mid")
        try {
            val expected = MidiNormalizer().normalize(partId, input, temporary, config)
            report == expected && report.output.sha256 == sha256(output)
        } finally { Files.deleteIfExists(temporary) }
    }.getOrDefault(false)
}
