package app.melotrail.arrangement

import java.math.BigInteger
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Immutable musical clock for an arranged song.
 *
 * Positions are canonical PPQ ticks.  Audio frame counts are deliberately
 * derived only from [secondsAt] at the final render boundary, never by adding
 * rounded section durations.  Section ranges are half-open: a note-on belongs
 * to [startTick, endTick), while a note-off at endTick is legal.
 */
@Serializable
data class SongTimeline(
    val version: Int = VERSION,
    val canonicalPpq: Int,
    val ppqConversions: List<PpqConversionEvidence>,
    val occurrences: List<SongTimelineOccurrence>,
    val transitions: List<SongTimelineTransitionRange>,
    val tempoMap: List<SongTimelineTempoEvent>,
    val timeSignatureMap: List<SongTimelineTimeSignatureEvent>,
    val totalTicks: Long,
    val totalSeconds: Double
) {
    init {
        require(version == VERSION) { "Unsupported song timeline version: $version" }
        require(canonicalPpq in 1..PpqNormalization.MAX_CANONICAL_PPQ) { "Canonical PPQ is invalid" }
        require(occurrences.isNotEmpty()) { "Song timeline requires at least one occurrence" }
        require(tempoMap.firstOrNull()?.tick == 0L) { "Song timeline requires tempo at tick zero" }
        require(timeSignatureMap.firstOrNull()?.tick == 0L) { "Song timeline requires a time signature at tick zero" }
        require(totalTicks > 0 && totalSeconds.isFinite() && totalSeconds > 0.0) { "Song timeline duration is invalid" }
    }

    fun occurrence(occurrenceId: String): SongTimelineOccurrence =
        occurrences.firstOrNull { it.occurrenceId == occurrenceId }
            ?: throw IllegalArgumentException("Unknown song occurrence '$occurrenceId'")

    fun conversion(inputId: String): PpqConversionEvidence =
        ppqConversions.firstOrNull { it.inputId == inputId }
            ?: throw IllegalArgumentException("Unknown PPQ conversion input '$inputId'")

    fun localToSongTick(occurrenceId: String, localTick: Long, noteOn: Boolean = false): Long {
        val occurrence = occurrence(occurrenceId)
        require(localTick >= 0) { "Local tick must not be negative" }
        val normalized = conversion(occurrence.inputId).toCanonical(localTick)
        if (noteOn) require(normalized < occurrence.lengthTicks) { "Note-on at local tick $localTick is outside occurrence '$occurrenceId'" }
        else require(normalized <= occurrence.lengthTicks) { "Tick $localTick is outside occurrence '$occurrenceId'" }
        return addExact(occurrence.startTick, normalized, "Song tick overflows")
    }

    fun acceptsNoteOn(occurrenceId: String, songTick: Long): Boolean {
        val occurrence = occurrence(occurrenceId)
        return songTick in occurrence.startTick until occurrence.endTick
    }

    fun acceptsNoteOff(occurrenceId: String, songTick: Long): Boolean {
        val occurrence = occurrence(occurrenceId)
        return songTick in occurrence.startTick..occurrence.endTick
    }

    fun secondsAt(songTick: Long): Double {
        require(songTick in 0..totalTicks) { "Song tick $songTick is outside 0..$totalTicks" }
        var seconds = 0.0
        tempoMap.forEachIndexed { index, event ->
            if (event.tick >= songTick) return@forEachIndexed
            val end = minOf(songTick, tempoMap.getOrNull(index + 1)?.tick ?: songTick)
            seconds += (end - event.tick).toDouble() * 60.0 / (event.bpm * canonicalPpq)
        }
        return seconds
    }

    fun frames(sampleRate: Int): Long {
        require(sampleRate in 8_000..384_000) { "Sample rate must be from 8000 to 384000" }
        val frames = secondsAt(totalTicks) * sampleRate
        require(frames.isFinite() && frames <= Long.MAX_VALUE.toDouble()) { "Song timeline frame count overflows" }
        return frames.roundToLong()
    }

    fun framesAt(songTick: Long, sampleRate: Int): Long {
        require(sampleRate in 8_000..384_000) { "Sample rate must be from 8000 to 384000" }
        val frames = secondsAt(songTick) * sampleRate
        require(frames.isFinite() && frames <= Long.MAX_VALUE.toDouble()) { "Song timeline frame count overflows" }
        return frames.roundToLong()
    }

    fun synchronizationReport(sampleRate: Int): SongSynchronizationReport =
        SongSynchronizationReporter.create(this, sampleRate)

    companion object {
        const val VERSION = 1

        fun create(input: SongTimelineInput): SongTimeline {
            input.requireValid()
            val normalization = PpqNormalization.plan(input.occurrences.map {
                PpqNormalizationInput(it.inputId, it.ppq, it.meterDivisors(), it.evidenceTicks())
            })
            val conversions = input.occurrences.map { occurrence ->
                occurrence.recordConversionEvidence(normalization.conversion(occurrence.inputId).withIdentity(occurrence.partId, occurrence.inputFingerprint))
            }
            val transitionRequests = input.transitions.associateBy { it.afterOccurrenceId }
            input.occurrences.forEachIndexed { index, occurrence ->
                transitionRequests[occurrence.occurrenceId]?.let { request ->
                    require(index < input.occurrences.lastIndex) { "Transition '${request.transitionId}' cannot follow the final occurrence" }
                }
            }
            var cursor = 0L
            val rebuiltOccurrences = mutableListOf<SongTimelineOccurrence>()
            val rebuiltTransitions = mutableListOf<SongTimelineTransitionRange>()
            input.occurrences.forEachIndexed { index, source ->
                val length = conversions.first { it.inputId == source.inputId }.toCanonical(source.durationTicks)
                val occurrence = SongTimelineOccurrence(source.occurrenceId, source.partId, source.inputId, source.inputFingerprint, source.ppq, cursor, addExact(cursor, length, "Occurrence range overflows"), length)
                rebuiltOccurrences += occurrence
                cursor = occurrence.endTick
                transitionRequests[source.occurrenceId]?.let { request ->
                    val incoming = input.occurrences[index + 1]
                    val transitionLength = multiplyExact(request.bars.toLong(), barTicks(normalization.canonicalPpq, incoming.timeSignatures.first()), "Transition length overflows")
                    val transition = SongTimelineTransitionRange(request.transitionId, source.occurrenceId, incoming.occurrenceId, cursor, addExact(cursor, transitionLength, "Transition range overflows"), incoming.occurrenceId, incoming.occurrenceId)
                    rebuiltTransitions += transition
                    cursor = transition.endTick
                }
            }
            val tempoMap = buildTempoMap(input.occurrences, rebuiltOccurrences, rebuiltTransitions, conversions)
            val signatures = buildSignatureMap(input.occurrences, rebuiltOccurrences, rebuiltTransitions, conversions)
            val seconds = durationSeconds(cursor, tempoMap, normalization.canonicalPpq)
            return SongTimeline(canonicalPpq = normalization.canonicalPpq, ppqConversions = conversions, occurrences = rebuiltOccurrences, transitions = rebuiltTransitions, tempoMap = tempoMap, timeSignatureMap = signatures, totalTicks = cursor, totalSeconds = seconds)
        }

        private fun buildTempoMap(inputs: List<SongTimelineOccurrenceInput>, occurrences: List<SongTimelineOccurrence>, transitions: List<SongTimelineTransitionRange>, conversions: List<PpqConversionEvidence>): List<SongTimelineTempoEvent> {
            val result = mutableListOf<SongTimelineTempoEvent>()
            occurrences.forEachIndexed { index, occurrence ->
                val input = inputs[index]
                val conversion = conversions.first { it.inputId == input.inputId }
                transitions.firstOrNull { it.afterOccurrenceId == occurrence.occurrenceId }?.let { transition ->
                    val incoming = inputs[index + 1]
                    result += SongTimelineTempoEvent(transition.startTick, incoming.tempoMap.first().bpm, incoming.occurrenceId, "transition")
                }
                input.tempoMap.forEach { tempo ->
                    result += SongTimelineTempoEvent(addExact(occurrence.startTick, conversion.toCanonical(tempo.tick), "Tempo position overflows"), tempo.bpm, occurrence.occurrenceId, "occurrence")
                }
            }
            return result.sortedBy { it.tick }.also { require(it.zipWithNext().all { (a, b) -> a.tick < b.tick }) { "PPQ conversion makes tempo events ambiguous" } }
        }

        private fun buildSignatureMap(inputs: List<SongTimelineOccurrenceInput>, occurrences: List<SongTimelineOccurrence>, transitions: List<SongTimelineTransitionRange>, conversions: List<PpqConversionEvidence>): List<SongTimelineTimeSignatureEvent> {
            val result = mutableListOf<SongTimelineTimeSignatureEvent>()
            occurrences.forEachIndexed { index, occurrence ->
                val input = inputs[index]
                val conversion = conversions.first { it.inputId == input.inputId }
                transitions.firstOrNull { it.afterOccurrenceId == occurrence.occurrenceId }?.let { transition ->
                    val incoming = inputs[index + 1]
                    val meter = incoming.timeSignatures.first()
                    result += SongTimelineTimeSignatureEvent(transition.startTick, meter.numerator, meter.denominator, incoming.occurrenceId, "transition")
                }
                input.timeSignatures.forEach { meter ->
                    result += SongTimelineTimeSignatureEvent(addExact(occurrence.startTick, conversion.toCanonical(meter.tick), "Time-signature position overflows"), meter.numerator, meter.denominator, occurrence.occurrenceId, "occurrence")
                }
            }
            return result.sortedBy { it.tick }.also { require(it.zipWithNext().all { (a, b) -> a.tick < b.tick }) { "PPQ conversion makes time-signature events ambiguous" } }
        }

        private fun durationSeconds(endTick: Long, tempos: List<SongTimelineTempoEvent>, ppq: Int): Double {
            var seconds = 0.0
            tempos.forEachIndexed { index, tempo ->
                if (tempo.tick >= endTick) return@forEachIndexed
                val end = minOf(endTick, tempos.getOrNull(index + 1)?.tick ?: endTick)
                seconds += (end - tempo.tick).toDouble() * 60.0 / (tempo.bpm * ppq)
            }
            require(seconds.isFinite() && seconds > 0.0) { "Song duration is invalid" }
            return seconds
        }

        private fun barTicks(ppq: Int, signature: MidiTimeSignature): Long {
            require((ppq * 4) % signature.denominator == 0) { "Canonical PPQ $ppq cannot represent ${signature.numerator}/${signature.denominator}" }
            return multiplyExact(signature.numerator.toLong(), (ppq * 4L) / signature.denominator, "Bar length overflows")
        }
    }
}

@Serializable
data class SongTimelineInput(val occurrences: List<SongTimelineOccurrenceInput>, val transitions: List<SongTimelineTransitionRequest> = emptyList()) {
    fun requireValid() {
        require(occurrences.isNotEmpty()) { "Song timeline requires occurrences" }
        require(occurrences.map { it.occurrenceId }.distinct().size == occurrences.size) { "Song occurrence IDs must be unique" }
        require(occurrences.map { it.inputId }.distinct().size == occurrences.size) { "Song timeline input IDs must be unique" }
        occurrences.forEach(SongTimelineOccurrenceInput::requireValid)
        require(transitions.map { it.transitionId }.distinct().size == transitions.size) { "Song transition IDs must be unique" }
        require(transitions.map { it.afterOccurrenceId }.distinct().size == transitions.size) { "Only one transition may follow an occurrence" }
        transitions.forEach { transition ->
            transition.requireValid()
            require(occurrences.any { it.occurrenceId == transition.afterOccurrenceId }) { "Transition '${transition.transitionId}' follows an unknown occurrence" }
        }
    }
}

@Serializable
data class SongTimelineOccurrenceInput(
    val occurrenceId: String,
    val partId: String,
    val inputId: String,
    val inputFingerprint: String,
    val ppq: Int,
    val durationTicks: Long,
    val tempoMap: List<MidiTempoChange>,
    val timeSignatures: List<MidiTimeSignature>,
    val alignmentEvidenceTicks: List<Long> = emptyList()
) {
    fun requireValid() {
        requireId(occurrenceId, "Occurrence ID")
        requireId(partId, "Part ID")
        requireId(inputId, "Input ID")
        require(inputFingerprint.matches(HEX_SHA256)) { "Input '$inputId' fingerprint must be a SHA-256 hex digest" }
        require(ppq > 0 && durationTicks > 0) { "Occurrence '$occurrenceId' has invalid PPQ or duration" }
        require(tempoMap.firstOrNull()?.tick == 0L && tempoMap.zipWithNext().all { (a, b) -> a.tick < b.tick } && tempoMap.all { it.tick in 0 until durationTicks && it.bpm.isFinite() && it.bpm in 20.0..400.0 }) { "Occurrence '$occurrenceId' has invalid tempo events" }
        require(timeSignatures.firstOrNull()?.tick == 0L && timeSignatures.zipWithNext().all { (a, b) -> a.tick < b.tick } && timeSignatures.all { it.tick in 0 until durationTicks && it.numerator in 1..32 && it.denominator in setOf(1, 2, 4, 8, 16, 32) }) { "Occurrence '$occurrenceId' has invalid time-signature events" }
        require(alignmentEvidenceTicks.all { it in 0..durationTicks }) { "Occurrence '$occurrenceId' has out-of-range alignment evidence" }
    }

    fun meterDivisors(): List<Int> = timeSignatures.map { it.denominator / gcd(it.denominator, 4) }
    fun evidenceTicks(): List<Long> = (listOf(0L, durationTicks) + tempoMap.map { it.tick } + timeSignatures.map { it.tick } + alignmentEvidenceTicks).distinct().sorted()

    fun recordConversionEvidence(conversion: PpqConversionEvidence): PpqConversionEvidence {
        val maxTickError = evidenceTicks().maxOf { conversion.tickError(it) }
        val maxTimeError = evidenceTicks().maxOf { tick ->
            val bpm = tempoMap.last { it.tick <= tick }.bpm
            conversion.tickError(tick) * 60.0 / (bpm * conversion.canonicalPpq)
        }
        require(maxTickError <= PpqNormalization.MAX_TICK_ERROR + 1e-12 && maxTimeError <= PpqNormalization.MAX_TIME_ERROR_SECONDS + 1e-12) {
            "PPQ conversion for '$inputId' exceeds tolerance: $maxTickError ticks, $maxTimeError seconds"
        }
        return conversion.copy(maximumTickError = maxTickError, maximumTimeErrorSeconds = maxTimeError)
    }

    private companion object { val HEX_SHA256 = Regex("[0-9a-f]{64}") }
}

@Serializable
data class SongTimelineTransitionRequest(val transitionId: String, val afterOccurrenceId: String, val bars: Int) {
    fun requireValid() {
        requireId(transitionId, "Transition ID")
        requireId(afterOccurrenceId, "Transition predecessor ID")
        require(bars in 1..16) { "Transition '$transitionId' bars must be from 1 to 16" }
    }
}

@Serializable data class SongTimelineOccurrence(val occurrenceId: String, val partId: String, val inputId: String, val inputFingerprint: String, val sourcePpq: Int, val startTick: Long, val endTick: Long, val lengthTicks: Long)
@Serializable data class SongTimelineTransitionRange(val transitionId: String, val afterOccurrenceId: String, val incomingOccurrenceId: String, val startTick: Long, val endTick: Long, val tempoOwnerOccurrenceId: String, val meterOwnerOccurrenceId: String)
@Serializable data class SongTimelineTempoEvent(val tick: Long, val bpm: Double, val ownerOccurrenceId: String, val owner: String)
@Serializable data class SongTimelineTimeSignatureEvent(val tick: Long, val numerator: Int, val denominator: Int, val ownerOccurrenceId: String, val owner: String)

/** Deterministic PPQ policy: exact LCM through 9,600; otherwise nearest-half-up rational conversion to 9,600 PPQ. */
object PpqNormalization {
    const val MAX_CANONICAL_PPQ = 9_600
    const val MAX_TICK_ERROR = 0.5
    const val MAX_TIME_ERROR_SECONDS = 0.0001

    fun plan(inputs: List<PpqNormalizationInput>): PpqNormalizationPlan {
        require(inputs.isNotEmpty()) { "PPQ normalization requires inputs" }
        require(inputs.map { it.inputId }.distinct().size == inputs.size) { "PPQ normalization input IDs must be unique" }
        inputs.forEach { requireId(it.inputId, "PPQ normalization input ID"); require(it.sourcePpq > 0); require(it.meterDivisors.all { divisor -> divisor > 0 && MAX_CANONICAL_PPQ % divisor == 0 }) }
        val values = inputs.flatMap { listOf(it.sourcePpq) + it.meterDivisors }
        var common: Long? = 1L
        values.forEach { value -> common = common?.let { lcmAtMost(it, value.toLong(), MAX_CANONICAL_PPQ.toLong()) } }
        val canonical = common?.toInt() ?: MAX_CANONICAL_PPQ
        return PpqNormalizationPlan(canonical, inputs.map { source ->
            val exact = canonical % source.sourcePpq == 0
            PpqConversionEvidence(source.inputId, source.sourcePpq, canonical, exact, source.evidenceTicks.maxOfOrNull { tickError(source.sourcePpq, canonical, it) } ?: 0.0, rounding = "nearest-half-up")
        })
    }

    private fun tickError(sourcePpq: Int, canonicalPpq: Int, tick: Long): Double = abs((tick.toBigInteger() * canonicalPpq.toBigInteger()).toDouble() / sourcePpq - roundHalfUp(tick.toBigInteger() * canonicalPpq.toBigInteger(), sourcePpq.toBigInteger()).toDouble())
    private fun lcmAtMost(left: Long, right: Long, limit: Long): Long? {
        val divisor = gcd(left.toInt(), right.toInt()).toLong()
        val reduced = left / divisor
        return if (reduced > limit / right) null else (reduced * right).takeIf { it <= limit }
    }
}

@Serializable data class PpqNormalizationInput(val inputId: String, val sourcePpq: Int, val meterDivisors: List<Int> = emptyList(), val evidenceTicks: List<Long> = emptyList())
@Serializable data class PpqNormalizationPlan(val canonicalPpq: Int, val conversions: List<PpqConversionEvidence>) {
    fun conversion(inputId: String): PpqConversionEvidence = conversions.first { it.inputId == inputId }
}

@Serializable
data class PpqConversionEvidence(
    val inputId: String,
    val sourcePpq: Int,
    val canonicalPpq: Int,
    val exact: Boolean,
    val maximumTickError: Double,
    val maximumTimeErrorSeconds: Double = 0.0,
    val rounding: String,
    val partId: String = "",
    val inputFingerprint: String = ""
) {
    fun toCanonical(sourceTick: Long): Long {
        require(sourceTick >= 0) { "Source tick must not be negative" }
        return try {
            roundHalfUp(sourceTick.toBigInteger() * canonicalPpq.toBigInteger(), sourcePpq.toBigInteger()).longValueExact()
        } catch (_: ArithmeticException) {
            throw IllegalArgumentException("Canonical PPQ tick overflows")
        }
    }
    fun toSource(canonicalTick: Long): Long {
        require(canonicalTick >= 0) { "Canonical tick must not be negative" }
        return try {
            roundHalfUp(canonicalTick.toBigInteger() * sourcePpq.toBigInteger(), canonicalPpq.toBigInteger()).longValueExact()
        } catch (_: ArithmeticException) {
            throw IllegalArgumentException("Source PPQ tick overflows")
        }
    }
    fun tickError(sourceTick: Long): Double = abs((sourceTick.toBigInteger() * canonicalPpq.toBigInteger()).toDouble() / sourcePpq - toCanonical(sourceTick).toDouble())
    fun withIdentity(partId: String, inputFingerprint: String): PpqConversionEvidence = copy(partId = partId, inputFingerprint = inputFingerprint)
}

@Serializable
data class SongSynchronizationReport(
    val version: Int = VERSION,
    val timelineFingerprint: String,
    val sampleRate: Int,
    val canonicalPpq: Int,
    val inputs: List<PpqConversionEvidence>,
    val occurrences: List<SongTimelineOccurrence>,
    val transitions: List<SongTimelineTransitionRange>,
    val tempoMap: List<SongTimelineTempoEvent>,
    val timeSignatureMap: List<SongTimelineTimeSignatureEvent>,
    val totalTicks: Long,
    val totalSeconds: Double,
    val totalFrames: Long,
    val maximumAlignmentErrorTicks: Double,
    val maximumAlignmentErrorSeconds: Double
) {
    companion object { const val VERSION = 1 }
}

object SongSynchronizationReporter {
    private val json = Json { encodeDefaults = true; prettyPrint = true }

    fun create(timeline: SongTimeline, sampleRate: Int): SongSynchronizationReport {
        val maxTicks = timeline.ppqConversions.maxOf { it.maximumTickError }
        val maxSeconds = timeline.ppqConversions.maxOf { it.maximumTimeErrorSeconds }
        val content = json.encodeToString(SongTimeline.serializer(), timeline)
        return SongSynchronizationReport(
            timelineFingerprint = sha256(content), sampleRate = sampleRate, canonicalPpq = timeline.canonicalPpq,
            inputs = timeline.ppqConversions, occurrences = timeline.occurrences, transitions = timeline.transitions,
            tempoMap = timeline.tempoMap, timeSignatureMap = timeline.timeSignatureMap, totalTicks = timeline.totalTicks,
            totalSeconds = timeline.totalSeconds, totalFrames = timeline.frames(sampleRate),
            maximumAlignmentErrorTicks = maxTicks, maximumAlignmentErrorSeconds = maxSeconds
        )
    }

    fun serialize(report: SongSynchronizationReport): String = json.encodeToString(report)
    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}

private fun roundHalfUp(numerator: BigInteger, denominator: BigInteger): BigInteger {
    val (whole, remainder) = numerator.divideAndRemainder(denominator)
    return if (remainder * BigInteger.TWO >= denominator) whole + BigInteger.ONE else whole
}
private fun Long.toBigInteger(): BigInteger = BigInteger.valueOf(this)
private fun Int.toBigInteger(): BigInteger = BigInteger.valueOf(toLong())
private fun gcd(left: Int, right: Int): Int { var a = abs(left); var b = abs(right); while (b != 0) { val next = a % b; a = b; b = next }; return a }
private fun addExact(left: Long, right: Long, message: String): Long = try { Math.addExact(left, right) } catch (_: ArithmeticException) { throw IllegalArgumentException(message) }
private fun multiplyExact(left: Long, right: Long, message: String): Long = try { Math.multiplyExact(left, right) } catch (_: ArithmeticException) { throw IllegalArgumentException(message) }
private val TIMELINE_ID = Regex("[A-Za-z0-9][A-Za-z0-9._-]*")
private fun requireId(value: String, label: String) { require(value.matches(TIMELINE_ID)) { "$label is invalid: $value" } }
