package app.melotrail.arrangement

import app.melotrail.audio.AudioBuffer
import app.melotrail.audio.AudioFormat
import app.melotrail.dsp.Compression
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

@Serializable
enum class AudioMixIssueKind { CLIPPING, LOW_HEADROOM, LOW_END_OVERLAP, MASKING, STEREO_CORRELATION, MELODY_AUDIBILITY }

@Serializable
enum class AudioMixIssueSeverity { WARNING, BLOCKING }

@Serializable
data class AudioMixIssue(val kind: AudioMixIssueKind, val severity: AudioMixIssueSeverity, val message: String)

@Serializable
data class StemLoudness(val stem: String, val rmsDbfs: Double, val peakDbfs: Double)

@Serializable
data class MelodyAudibility(val melodyStem: String, val rmsDbfs: Double, val accompanimentRmsDbfs: Double, val signalToAccompanimentDb: Double, val audible: Boolean)

@Serializable
data class AudioMixCriticReport(
    val version: Int = 1,
    val planSha256: String,
    val mixSha256: String,
    val peakDbfs: Double,
    val headroomDb: Double,
    val clippingSampleCount: Int,
    val stereoCorrelation: Double?,
    val stemLoudness: List<StemLoudness>,
    val melodyAudibility: MelodyAudibility?,
    val issues: List<AudioMixIssue>
) {
    val commercialReady: Boolean get() = issues.none { it.severity == AudioMixIssueSeverity.BLOCKING }
}

/**
 * Deterministic production renderer. The older [DeterministicStemMixer] is
 * intentionally retained as the unprocessed reference/debug renderer.
 */
class ProductionStemMixer(private val referenceMixer: DeterministicStemMixer = DeterministicStemMixer()) {
    fun mix(tracks: List<MixTrack>, plan: MixPlan, format: RenderFormat): MixedStem {
        plan.requireValid()
        require(tracks.isNotEmpty()) { "No rendered stems available for production mixing" }
        require(format.channels in 1..2) { "Production mixer supports mono or stereo render formats" }
        val names = tracks.map(MixTrack::name).toSet()
        require(names.all { it in MixPlan.logicalNames }) { "Production mixer received an unsupported stem" }
        val soloed = tracks.any { plan.tracks[it.name]?.solo == true }
        val active = tracks.filter { track ->
            val setting = plan.tracks[track.name] ?: MixTrackPlan()
            !setting.muted && (!soloed || setting.solo)
        }
        require(active.isNotEmpty()) { "Production mix has no audible stems" }
        val frames = active.maxOf { it.buffer.length + it.startFrame }
        val channels = format.channels
        val direct = FloatArray(frames * channels)
        val roomInput = FloatArray(frames * channels)
        val busBuffers = MixBus.entries.filter { it != MixBus.DIRECT }.associateWith { FloatArray(frames * channels) }

        active.forEach { track ->
            val setting = plan.tracks[track.name] ?: MixTrackPlan()
            var processed = toFormat(track.buffer, format)
            processed = filter(processed, format, setting.filter)
            processed = equalize(processed, format, setting.eq)
            if (setting.compression.enabled) processed = compress(processed, format, setting.compression)
            if (channels == 2) processed = width(processed, setting.stereoWidth)
            val target = if (setting.bus == MixBus.DIRECT) direct else busBuffers.getValue(setting.bus)
            addTrack(target, roomInput, processed, track.startFrame, setting, format)
        }
        busBuffers.forEach { (bus, buffer) ->
            val busPlan = plan.buses[bus] ?: MixBusPlan(enabled = false)
            if (!busPlan.enabled) {
                addInPlace(direct, buffer, 1f)
                return@forEach
            }
            var processed = buffer
            if (busPlan.compression.enabled) processed = compress(processed, format, busPlan.compression)
            addInPlace(direct, processed, dbGain(busPlan.gainDb))
        }
        if (plan.room.enabled && plan.room.mix > 0.0) addInPlace(direct, room(roomInput, format, plan.room), plan.room.mix.toFloat())
        val buffer = AudioBuffer(direct, AudioFormat(format.sampleRate, channels, 24, false, false, "WAV"), frames.toDouble() / format.sampleRate)
        return referenceMixer.mix(listOf(MixTrack("production", buffer)), MixSettings(requiredFormat = format, peakCeiling = 0.89)).copy(includedTracks = active.map(MixTrack::name))
    }

    private fun addTrack(destination: FloatArray, roomInput: FloatArray, source: FloatArray, startFrame: Int, setting: MixTrackPlan, format: RenderFormat) {
        for (frame in 0 until source.size / format.channels) {
            val automation = setting.sectionAutomation.firstOrNull { frame + startFrame in it.startFrame until it.endFrameExclusive }
            val gain = dbGain(automation?.gainDb ?: setting.gainDb)
            val send = (automation?.reverbSend ?: setting.reverbSend).toFloat()
            val pan = automation?.pan ?: setting.pan
            val destinationFrame = frame + startFrame
            for (channel in 0 until format.channels) {
                val sourceIndex = frame * format.channels + channel
                val targetIndex = destinationFrame * format.channels + channel
                val panGain = if (format.channels == 2) constantPowerPan(channel, pan) else 1f
                destination[targetIndex] += source[sourceIndex] * gain * panGain
                roomInput[targetIndex] += source[sourceIndex] * gain * send * panGain
            }
        }
    }

    private fun toFormat(input: AudioBuffer, format: RenderFormat): FloatArray {
        require(input.format.sampleRate == format.sampleRate && input.format.channels == format.channels && input.format.bitDepth == 24) {
            "Production mixer requires render-format-compatible stems"
        }
        return input.samples.clone()
    }

    private fun filter(input: FloatArray, format: RenderFormat, plan: FilterPlan): FloatArray {
        var result = input
        plan.highPassHz?.let { result = highPass(result, format, it) }
        plan.lowPassHz?.let { result = lowPass(result, format, it) }
        return result
    }

    private fun lowPass(input: FloatArray, format: RenderFormat, cutoff: Double): FloatArray {
        val alpha = (1.0 - exp(-2.0 * PI * cutoff / format.sampleRate)).toFloat()
        val previous = FloatArray(format.channels)
        return FloatArray(input.size) { index ->
            val channel = index % format.channels
            previous[channel] += alpha * (input[index] - previous[channel])
            previous[channel]
        }
    }

    private fun highPass(input: FloatArray, format: RenderFormat, cutoff: Double): FloatArray {
        val low = lowPass(input, format, cutoff)
        return FloatArray(input.size) { index -> input[index] - low[index] }
    }

    private fun equalize(input: FloatArray, format: RenderFormat, bands: List<EqBandPlan>): FloatArray {
        var result = input
        bands.forEach { band ->
            val low = lowPass(result, format, band.frequencyHz)
            val factor = dbGain(band.gainDb)
            result = FloatArray(result.size) { index ->
                val selected = if (band.frequencyHz < 500.0) low[index] else result[index] - low[index]
                result[index] + selected * (factor - 1f)
            }
        }
        return result
    }

    private fun compress(input: FloatArray, format: RenderFormat, plan: CompressionPlan): FloatArray =
        Compression(1.0, plan.thresholdDb, plan.ratio, format.sampleRate, format.channels).process(input)
            .let { samples -> FloatArray(samples.size) { index -> samples[index] * dbGain(plan.makeupDb) } }

    private fun width(input: FloatArray, width: Double): FloatArray = FloatArray(input.size) { index ->
        val frame = index / 2 * 2
        val mid = (input[frame] + input[frame + 1]) * 0.5f
        val side = (input[frame] - input[frame + 1]) * 0.5f * width.toFloat()
        if (index % 2 == 0) mid + side else mid - side
    }

    private fun room(input: FloatArray, format: RenderFormat, plan: SharedRoomPlan): FloatArray {
        val delayFrames = (format.sampleRate * 0.035).toInt().coerceAtLeast(1)
        val output = FloatArray(input.size)
        val feedback = exp(-3.0 * delayFrames / (format.sampleRate * plan.decaySeconds)).toFloat()
        for (frame in 0 until input.size / format.channels) for (channel in 0 until format.channels) {
            val index = frame * format.channels + channel
            val delayed = if (frame >= delayFrames) output[(frame - delayFrames) * format.channels + channel] else 0f
            output[index] = input[index] + delayed * feedback
        }
        return lowPass(output, format, 7_000.0)
    }

    private fun addInPlace(destination: FloatArray, addition: FloatArray, gain: Float) = destination.indices.forEach { index -> destination[index] += addition[index] * gain }
    private fun dbGain(db: Double): Float = 10.0.pow(db / 20.0).toFloat()
    private fun constantPowerPan(channel: Int, pan: Double): Float = sqrt(if (channel == 0) (1.0 - pan) / 2.0 else (1.0 + pan) / 2.0).toFloat() * sqrt(2.0).toFloat()
}

/** Objective measurement only; it never changes a mix or its plan. */
object AudioMixCritic {
    fun analyze(mix: MixedStem, stems: List<MixTrack>, planSha256: String, mixSha256: String): AudioMixCriticReport {
        val audio = mix.buffer
        val peak = audio.samples.maxOfOrNull { abs(it.toDouble()) } ?: 0.0
        val peakDb = db(peak)
        val clipping = audio.samples.count { abs(it) >= 0.999f }
        val loudness = stems.sortedBy(MixTrack::name).map { stem -> StemLoudness(stem.name, db(rms(stem.buffer.samples)), db(stem.buffer.samples.maxOf { abs(it.toDouble()) })) }
        val melody = stems.singleOrNull { it.name == LogicalInstrument.PIANO.wireName }?.let { piano ->
            val other = sumOthers(stems.filterNot { it.name == piano.name }, piano.buffer.samples.size)
            val melodyRms = rms(piano.buffer.samples); val otherRms = rms(other); val ratio = db(melodyRms / max(otherRms, 1e-9))
            MelodyAudibility(piano.name, db(melodyRms), db(otherRms), ratio, ratio >= -6.0)
        }
        val correlation = if (audio.format.channels == 2) correlation(audio.samples) else null
        val issues = buildList {
            if (clipping > 0) add(AudioMixIssue(AudioMixIssueKind.CLIPPING, AudioMixIssueSeverity.BLOCKING, "$clipping samples reach PCM clipping"))
            if (20.0 * log10(1.0 / max(peak, 1e-12)) < 1.0) add(AudioMixIssue(AudioMixIssueKind.LOW_HEADROOM, AudioMixIssueSeverity.BLOCKING, "Peak headroom is below 1 dB"))
            if (lowEndOverlap(stems)) add(AudioMixIssue(AudioMixIssueKind.LOW_END_OVERLAP, AudioMixIssueSeverity.WARNING, "Low-frequency stem energy overlaps"))
            melody?.takeUnless(MelodyAudibility::audible)?.let { add(AudioMixIssue(AudioMixIssueKind.MELODY_AUDIBILITY, AudioMixIssueSeverity.BLOCKING, "Melody is ${"%.1f".format(it.signalToAccompanimentDb)} dB below accompaniment")) }
            melody?.takeIf { it.signalToAccompanimentDb < -3.0 }?.let { add(AudioMixIssue(AudioMixIssueKind.MASKING, AudioMixIssueSeverity.WARNING, "Melody masking is elevated")) }
            correlation?.takeIf { it > 0.98 || it < -0.5 }?.let { add(AudioMixIssue(AudioMixIssueKind.STEREO_CORRELATION, AudioMixIssueSeverity.WARNING, "Stereo correlation is ${"%.2f".format(it)}")) }
        }
        return AudioMixCriticReport(planSha256 = planSha256, mixSha256 = mixSha256, peakDbfs = peakDb, headroomDb = 20.0 * log10(1.0 / max(peak, 1e-12)), clippingSampleCount = clipping, stereoCorrelation = correlation, stemLoudness = loudness, melodyAudibility = melody, issues = issues)
    }

    private fun sumOthers(stems: List<MixTrack>, size: Int): FloatArray = FloatArray(size).also { result -> stems.forEach { stem -> stem.buffer.samples.indices.forEach { index -> if (index < result.size) result[index] += stem.buffer.samples[index] } } }
    private fun lowEndOverlap(stems: List<MixTrack>): Boolean = stems.any { first -> stems.any { second -> first.name < second.name && rms(lowPass(first.buffer.samples, first.buffer.format.channels)) > 0.08 && rms(lowPass(second.buffer.samples, second.buffer.format.channels)) > 0.08 } }
    private fun lowPass(samples: FloatArray, channels: Int): FloatArray { val previous = FloatArray(channels); return FloatArray(samples.size) { index -> val c = index % channels; previous[c] += 0.02f * (samples[index] - previous[c]); previous[c] } }
    private fun correlation(samples: FloatArray): Double { val frames = samples.size / 2; if (frames < 2) return 1.0; val l = DoubleArray(frames) { samples[it * 2].toDouble() }; val r = DoubleArray(frames) { samples[it * 2 + 1].toDouble() }; val lm = l.average(); val rm = r.average(); val numerator = l.indices.sumOf { (l[it] - lm) * (r[it] - rm) }; val denominator = sqrt(l.sumOf { (it - lm) * (it - lm) } * r.sumOf { (it - rm) * (it - rm) }); return if (denominator <= 1e-12) 1.0 else numerator / denominator }
    private fun rms(samples: FloatArray): Double = sqrt(samples.map { it.toDouble() * it }.average())
    private fun db(value: Double): Double = 20.0 * log10(max(value, 1e-9))
}
