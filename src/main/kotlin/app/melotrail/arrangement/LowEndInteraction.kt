package app.melotrail.arrangement

import app.melotrail.audio.AudioBuffer
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Versioned ownership decision for the 50–150 Hz kick/bass interaction boundary. */
@Serializable
enum class LowEndSpectralOwner { KICK, BASS, NONE }

/** The explicit state of a measured low-end decision; retained files do not make it pass. */
@Serializable
enum class LowEndInteractionStatus { NOT_APPLICABLE, MONITORING, ACTIVE, BLOCKED }

/** One approved drum-MIDI kick attack mapped onto the rendered-stem frame timeline. */
@Serializable
data class LowEndKickTrigger(val tick: Long, val frame: Int) {
    init { require(tick >= 0 && frame >= 0) { "Low-end kick trigger is invalid" } }
}

/** Time- and band-aware kick/bass measurements for an exact mix revision. */
@Serializable
data class LowEndBandMetrics(
    val bandStartHz: Double = 50.0,
    val bandEndHz: Double = 150.0,
    val kickRmsDbfs: Double,
    val bassRmsDbfs: Double,
    val overlapRatio: Double,
    val coincidentWindows: Int,
    val eligibleWindows: Int,
    val coincidentWindowRatio: Double,
    val combinedPeakDbfs: Double
) {
    init {
        require(bandStartHz == 50.0 && bandEndHz == 150.0 && listOf(kickRmsDbfs, bassRmsDbfs, overlapRatio,
            coincidentWindowRatio, combinedPeakDbfs).all(Double::isFinite) && overlapRatio in 0.0..1.0 &&
            coincidentWindows >= 0 && eligibleWindows >= 0 && coincidentWindows <= eligibleWindows &&
            coincidentWindowRatio in 0.0..1.0) { "Low-end band metrics are invalid" }
    }
}

/** Hash-bound, deterministic low-end processing contract persisted with one production mix. */
@Serializable
data class LowEndInteractionPlan(
    val version: Int = VERSION,
    val processorId: String = PROCESSOR_ID,
    val processorSha256: String = PROCESSOR_SHA256,
    val status: LowEndInteractionStatus,
    val drumStemSha256: String? = null,
    val bassStemSha256: String? = null,
    val drumMidiSha256: String? = null,
    val drumValidationReportSha256: String? = null,
    /** Digest of every exact input stem in the persisted mix contract. */
    val mixInputsSha256: String,
    val kickMidiChannel: Int? = null,
    val kickMidiNote: Int? = null,
    val triggers: List<LowEndKickTrigger> = emptyList(),
    val attackMs: Double = ATTACK_MS,
    val holdMs: Double = HOLD_MS,
    val releaseMs: Double = RELEASE_MS,
    val latencyCompensationFrames: Int = 0,
    val duckingDb: Double = 0.0,
    /** Calibrated hypothesis, never a blind global 40 Hz default. */
    val bassHighPassHz: Double? = null,
    /** Calibrated 80 Hz ownership hypothesis, applied only to the non-owner. */
    val ownershipCutHz: Double? = null,
    val ownershipCutDb: Double = 0.0,
    val spectralOwner: LowEndSpectralOwner = LowEndSpectralOwner.NONE,
    /** Explicit failed-evidence reasons. A blocked plan can never be mistaken for a passing no-op. */
    val blockers: List<String> = emptyList(),
    val before: LowEndBandMetrics
) {
    init {
        require(version == VERSION && processorId == PROCESSOR_ID && SHA.matches(processorSha256) && SHA.matches(mixInputsSha256)) {
            "Unsupported low-end interaction plan"
        }
        listOf(drumStemSha256, bassStemSha256, drumMidiSha256, drumValidationReportSha256).filterNotNull().forEach {
            require(SHA.matches(it)) { "Low-end interaction input fingerprint is invalid" }
        }
        require(triggers == triggers.sortedWith(compareBy(LowEndKickTrigger::frame).thenBy(LowEndKickTrigger::tick)) &&
            triggers.map(LowEndKickTrigger::frame).distinct().size == triggers.size &&
            attackMs == ATTACK_MS && holdMs == HOLD_MS && releaseMs == RELEASE_MS && latencyCompensationFrames in 0..4_096) {
            "Low-end interaction envelope is invalid"
        }
        require(duckingDb == 0.0 || duckingDb in 2.0..4.0) { "Low-end duck must be 2–4 dB when active" }
        bassHighPassHz?.let { require(it in 30.0..60.0) { "Calibrated bass high-pass is invalid" } }
        ownershipCutHz?.let { require(it == 80.0) { "Only the versioned 80 Hz ownership hypothesis is supported" } }
        require(ownershipCutDb == 0.0 || ownershipCutDb in 1.0..4.0) { "Low-end ownership cut is invalid" }
        when (status) {
            LowEndInteractionStatus.NOT_APPLICABLE -> require(drumStemSha256 == null || bassStemSha256 == null) { "Applicable stems require a measured low-end plan" }
            LowEndInteractionStatus.MONITORING -> require(drumStemSha256 != null && bassStemSha256 != null && triggers.isNotEmpty() && duckingDb == 0.0) { "Monitoring plan is incomplete" }
            LowEndInteractionStatus.ACTIVE -> require(drumStemSha256 != null && bassStemSha256 != null && kickMidiChannel != null && kickMidiNote != null &&
                triggers.isNotEmpty() && duckingDb in 2.0..4.0 && spectralOwner != LowEndSpectralOwner.NONE) { "Active low-end plan is incomplete" }
            LowEndInteractionStatus.BLOCKED -> require(drumStemSha256 != null && bassStemSha256 != null && blockers.isNotEmpty()) {
                "Blocked low-end plan is incomplete"
            }
        }
        require(blockers == blockers.map(String::trim).filter(String::isNotBlank).distinct().sorted()) {
            "Low-end interaction blockers are invalid"
        }
    }

    companion object {
        const val VERSION = 1
        const val PROCESSOR_ID = "low-end-interaction-v1"
        const val ATTACK_MS = 8.0
        const val HOLD_MS = 40.0
        const val RELEASE_MS = 110.0
        const val PROCESSOR_SHA256 = "0b021a1f7c7249a4ccebba3e2c490c2b075b41e56e8e58f9db85a4466f8cd954"
        private val SHA = Regex("[0-9a-f]{64}")
    }
}

/** Before/after evidence persisted by [AudioMixCritic] for one exact low-end plan. */
@Serializable
data class LowEndInteractionReport(
    val plan: LowEndInteractionPlan,
    val after: LowEndBandMetrics,
    val severeUnresolvedOverlap: Boolean,
    val timingPreserved: Boolean,
    val durationPreserved: Boolean,
    val pumpingDetected: Boolean
)

/** Derives measured ownership and bounded ducking; it never reads a waveform to guess kick events. */
object LowEndInteractionPlanner {
    const val COLLISION_RATIO = 0.42
    const val COINCIDENT_WINDOW_RATIO = 0.30

    fun derive(
        drums: AudioBuffer?,
        bass: AudioBuffer?,
        drumStemSha256: String?,
        bassStemSha256: String?,
        drumMidiSha256: String?,
        drumValidationReportSha256: String?,
        mixInputsSha256: String,
        kickMidiChannel: Int?,
        kickMidiNote: Int?,
        triggers: List<LowEndKickTrigger>
    ): LowEndInteractionPlan {
        val metrics = LowEndBandAnalyzer.measure(drums, bass)
        if (drums == null || bass == null) return LowEndInteractionPlan(
            status = LowEndInteractionStatus.NOT_APPLICABLE, drumStemSha256 = drumStemSha256, bassStemSha256 = bassStemSha256,
            drumMidiSha256 = drumMidiSha256, drumValidationReportSha256 = drumValidationReportSha256, mixInputsSha256 = mixInputsSha256,
            before = metrics
        )
        require(drums.format.sampleRate == bass.format.sampleRate && drums.format.channels == bass.format.channels && drums.length == bass.length) {
            "Low-end interaction requires aligned drum and bass stems"
        }
        require(kickMidiChannel in 0..15 && kickMidiNote in 0..127 && triggers.isNotEmpty() && drumMidiSha256 != null && drumValidationReportSha256 != null) {
            "Low-end interaction requires approved drum kick-map evidence"
        }
        val collision = metrics.overlapRatio >= COLLISION_RATIO && metrics.coincidentWindowRatio >= COINCIDENT_WINDOW_RATIO
        if (!collision) return LowEndInteractionPlan(
            status = LowEndInteractionStatus.MONITORING, drumStemSha256 = drumStemSha256, bassStemSha256 = bassStemSha256,
            drumMidiSha256 = drumMidiSha256, drumValidationReportSha256 = drumValidationReportSha256, mixInputsSha256 = mixInputsSha256,
            kickMidiChannel = kickMidiChannel, kickMidiNote = kickMidiNote, triggers = triggers, before = metrics
        )
        val owner = if (metrics.kickRmsDbfs >= metrics.bassRmsDbfs) LowEndSpectralOwner.KICK else LowEndSpectralOwner.BASS
        // Three dB is the calibrated starting reference; stronger measured overlap may request at most four.
        val duck = (3.0 + (metrics.overlapRatio - COLLISION_RATIO) / (1.0 - COLLISION_RATIO)).coerceIn(2.0, 4.0)
        return LowEndInteractionPlan(
            status = LowEndInteractionStatus.ACTIVE, drumStemSha256 = drumStemSha256, bassStemSha256 = bassStemSha256,
            drumMidiSha256 = drumMidiSha256, drumValidationReportSha256 = drumValidationReportSha256, mixInputsSha256 = mixInputsSha256,
            kickMidiChannel = kickMidiChannel, kickMidiNote = kickMidiNote, triggers = triggers,
            duckingDb = duck, bassHighPassHz = if (LowEndBandAnalyzer.subEnergy(bass) > LowEndBandAnalyzer.bandEnergy(bass) * 0.55) 40.0 else 32.0,
            ownershipCutHz = 80.0, ownershipCutDb = 2.0, spectralOwner = owner, before = metrics
        )
    }
}

/** Applies only the persisted bounded plan and keeps sample count/timing unchanged. */
object LowEndInteractionProcessor {
    fun process(track: String, audio: AudioBuffer, plan: LowEndInteractionPlan?): AudioBuffer {
        if (plan?.status != LowEndInteractionStatus.ACTIVE || track !in setOf("bass", "drums")) return audio
        var samples = audio.samples.clone()
        if (track == "bass") {
            plan.bassHighPassHz?.let { samples = highPass(samples, audio.format.channels, audio.format.sampleRate, it) }
            if (plan.spectralOwner == LowEndSpectralOwner.KICK) samples = bandCut(samples, audio.format.channels, audio.format.sampleRate, requireNotNull(plan.ownershipCutHz), plan.ownershipCutDb)
            samples = duck(samples, audio.format.channels, audio.format.sampleRate, plan)
        } else if (plan.spectralOwner == LowEndSpectralOwner.BASS) {
            samples = bandCut(samples, audio.format.channels, audio.format.sampleRate, requireNotNull(plan.ownershipCutHz), plan.ownershipCutDb)
        }
        return audio.copy(samples = samples)
    }

    private fun duck(samples: FloatArray, channels: Int, sampleRate: Int, plan: LowEndInteractionPlan): FloatArray {
        val frames = samples.size / channels
        val envelopeDb = DoubleArray(frames)
        val attack = (sampleRate * plan.attackMs / 1_000.0).roundToInt().coerceAtLeast(1)
        val hold = (sampleRate * plan.holdMs / 1_000.0).roundToInt()
        val release = (sampleRate * plan.releaseMs / 1_000.0).roundToInt().coerceAtLeast(1)
        plan.triggers.forEach { trigger ->
            val start = trigger.frame + plan.latencyCompensationFrames
            for (offset in 0 until attack + hold + release) {
                val frame = start + offset
                if (frame !in 0 until frames) continue
                val depth = when {
                    offset < attack -> plan.duckingDb * (offset + 1).toDouble() / attack
                    offset < attack + hold -> plan.duckingDb
                    else -> plan.duckingDb * (1.0 - (offset - attack - hold + 1).toDouble() / release)
                }.coerceAtLeast(0.0)
                envelopeDb[frame] = max(envelopeDb[frame], depth)
            }
        }
        return FloatArray(samples.size) { index -> (samples[index] * 10.0.pow(-envelopeDb[index / channels] / 20.0)).toFloat() }
    }

    private fun bandCut(samples: FloatArray, channels: Int, sampleRate: Int, centre: Double, cutDb: Double): FloatArray {
        val band = lowPass(highPass(samples, channels, sampleRate, centre * 0.625), channels, sampleRate, centre * 1.875)
        val factor = 10.0.pow(-cutDb / 20.0).toFloat()
        return FloatArray(samples.size) { index -> samples[index] + band[index] * (factor - 1f) }
    }

    internal fun lowPass(samples: FloatArray, channels: Int, sampleRate: Int, cutoff: Double): FloatArray {
        val alpha = (1.0 - exp(-2.0 * PI * cutoff / sampleRate)).toFloat()
        val previous = FloatArray(channels)
        return FloatArray(samples.size) { index ->
            val channel = index % channels
            previous[channel] += alpha * (samples[index] - previous[channel])
            previous[channel]
        }
    }

    internal fun highPass(samples: FloatArray, channels: Int, sampleRate: Int, cutoff: Double): FloatArray {
        val low = lowPass(samples, channels, sampleRate, cutoff)
        return FloatArray(samples.size) { index -> samples[index] - low[index] }
    }
}

/** Deterministic 50–150 Hz time-window measurements; no broad low-passed proxy is used. */
object LowEndBandAnalyzer {
    private const val WINDOW_SECONDS = 0.050

    fun measure(drums: AudioBuffer?, bass: AudioBuffer?): LowEndBandMetrics {
        if (drums == null || bass == null) return LowEndBandMetrics(kickRmsDbfs = -180.0, bassRmsDbfs = -180.0, overlapRatio = 0.0,
            coincidentWindows = 0, eligibleWindows = 0, coincidentWindowRatio = 0.0, combinedPeakDbfs = -180.0)
        require(drums.format.sampleRate == bass.format.sampleRate && drums.format.channels == bass.format.channels && drums.length == bass.length) {
            "Low-end measurement requires aligned stems"
        }
        val channels = drums.format.channels
        val kick = bandSignal(drums); val bassBand = bandSignal(bass)
        val kickEnergy = energy(kick); val bassEnergy = energy(bassBand)
        val overlap = kick.indices.sumOf { abs(kick[it].toDouble()) * abs(bassBand[it].toDouble()) } / kick.size.coerceAtLeast(1)
        val ratio = (overlap / sqrt(max(kickEnergy * bassEnergy, 1e-18))).coerceIn(0.0, 1.0)
        val window = (drums.format.sampleRate * WINDOW_SECONDS).roundToInt().coerceAtLeast(1)
        val frames = drums.length
        var eligible = 0; var coincident = 0
        for (start in 0 until frames step window) {
            val end = min(frames, start + window)
            val kr = frameRms(kick, channels, start, end); val br = frameRms(bassBand, channels, start, end)
            if (kr > 0.003 || br > 0.003) eligible++
            if (kr > 0.003 && br > 0.003) coincident++
        }
        val combinedPeak = kick.indices.maxOfOrNull { abs(kick[it].toDouble() + bassBand[it].toDouble()) } ?: 0.0
        return LowEndBandMetrics(
            kickRmsDbfs = db(sqrt(kickEnergy)), bassRmsDbfs = db(sqrt(bassEnergy)), overlapRatio = ratio,
            coincidentWindows = coincident, eligibleWindows = eligible, coincidentWindowRatio = if (eligible == 0) 0.0 else coincident.toDouble() / eligible,
            combinedPeakDbfs = db(combinedPeak)
        )
    }

    fun bandEnergy(audio: AudioBuffer): Double = energy(bandSignal(audio))
    fun subEnergy(audio: AudioBuffer): Double = energy(LowEndInteractionProcessor.lowPass(audio.samples, audio.format.channels, audio.format.sampleRate, 40.0))

    private fun bandSignal(audio: AudioBuffer): FloatArray = LowEndInteractionProcessor.lowPass(
        LowEndInteractionProcessor.highPass(audio.samples, audio.format.channels, audio.format.sampleRate, 50.0),
        audio.format.channels, audio.format.sampleRate, 150.0
    )
    private fun energy(samples: FloatArray): Double = samples.sumOf { it.toDouble() * it } / samples.size.coerceAtLeast(1)
    private fun frameRms(samples: FloatArray, channels: Int, start: Int, end: Int): Double {
        var sum = 0.0; var count = 0
        for (frame in start until end) for (channel in 0 until channels) { val value = samples[frame * channels + channel].toDouble(); sum += value * value; count++ }
        return sqrt(sum / count.coerceAtLeast(1))
    }
    private fun db(value: Double): Double = 20.0 * log10(max(value, 1e-9))
}
