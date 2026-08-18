package app.melotrail.audio

import app.melotrail.model.ErrorReporter
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Read-only, deterministic A/B measurements for lossless WAV debugging.
 *
 * Spectral values use a Hann-windowed 2048-point FFT with a 1024-frame hop.
 * Power is averaged across every analyzed channel and window. Short inputs use
 * one zero-padded window so they still produce comparable, finite metrics.
 */
object AudioComparison {
    const val CHANGE_TOLERANCE = 1.0e-6
    const val FFT_WINDOW_SIZE = 2048
    const val FFT_HOP_SIZE = 1024
    private const val DB_FLOOR = -240.0

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    fun compareFiles(aPath: Path, bPath: Path, allowAlignment: Boolean = false): AudioComparisonReport {
        validateFile(aPath, "A")
        validateFile(bPath, "B")
        val decoder = WAVDecoder(ErrorReporter.NoOp)
        return compare(decoder.decode(aPath), decoder.decode(bPath), allowAlignment)
    }

    fun compare(a: AudioBuffer, b: AudioBuffer, allowAlignment: Boolean = false): AudioComparisonReport {
        validateBuffer(a, "A")
        validateBuffer(b, "B")
        require(a.format.sampleRate == b.format.sampleRate) {
            "Sample-rate mismatch: A is ${a.format.sampleRate} Hz, B is ${b.format.sampleRate} Hz. Resampling is never implicit."
        }
        require(a.format.channels == b.format.channels) {
            "Channel-count mismatch: A has ${a.format.channels}, B has ${b.format.channels}."
        }
        require(a.format.bitDepth == b.format.bitDepth && a.format.isFloat == b.format.isFloat) {
            "Sample-format mismatch: A is ${a.format.bitDepth}-bit${if (a.format.isFloat) " float" else " PCM"}, " +
                "B is ${b.format.bitDepth}-bit${if (b.format.isFloat) " float" else " PCM"}."
        }

        val aFrames = a.length
        val bFrames = b.length
        val alignmentMismatch = aFrames != bFrames
        require(!alignmentMismatch || allowAlignment) {
            "Timeline mismatch: A has $aFrames frames and B has $bFrames frames. Use --align only for diagnostic truncation."
        }
        val comparedFrames = minOf(aFrames, bFrames)
        require(comparedFrames > 0) { "Audio inputs must contain at least one frame." }

        var sumASquared = 0.0
        var sumBSquared = 0.0
        var aPeak = 0.0
        var bPeak = 0.0
        var sumDifferenceSquared = 0.0
        var sumAbsoluteDifference = 0.0
        var maxAbsoluteDifference = 0.0
        var changedFrames = 0
        val channels = a.format.channels

        for (frame in 0 until comparedFrames) {
            var changed = false
            val base = frame * channels
            for (channel in 0 until channels) {
                val aSample = a.samples[base + channel].toDouble()
                val bSample = b.samples[base + channel].toDouble()
                val difference = abs(aSample - bSample)
                sumASquared += aSample * aSample
                sumBSquared += bSample * bSample
                aPeak = max(aPeak, abs(aSample))
                bPeak = max(bPeak, abs(bSample))
                sumDifferenceSquared += difference * difference
                sumAbsoluteDifference += difference
                maxAbsoluteDifference = max(maxAbsoluteDifference, difference)
                if (difference > CHANGE_TOLERANCE) changed = true
            }
            if (changed) changedFrames++
        }

        val sampleCount = comparedFrames.toLong() * channels
        val aRms = sqrt(sumASquared / sampleCount)
        val bRms = sqrt(sumBSquared / sampleCount)
        val spectrumA = spectralMetrics(a, comparedFrames)
        val spectrumB = spectralMetrics(b, comparedFrames)
        return AudioComparisonReport(
            tolerance = CHANGE_TOLERANCE,
            alignmentMode = allowAlignment,
            alignmentMismatch = alignmentMismatch,
            comparedFrameCount = comparedFrames,
            frameCountDifference = bFrames - aFrames,
            durationDifferenceSeconds = (bFrames - aFrames).toDouble() / a.format.sampleRate,
            a = SignalMetrics(a.format.sampleRate, channels, aFrames, aFrames.toDouble() / a.format.sampleRate, aRms, db(aRms), aPeak, spectrumA),
            b = SignalMetrics(b.format.sampleRate, channels, bFrames, bFrames.toDouble() / b.format.sampleRate, bRms, db(bRms), bPeak, spectrumB),
            rmsAbsoluteDelta = abs(aRms - bRms),
            rmsDeltaDb = db(bRms) - db(aRms),
            peakAbsoluteDelta = abs(aPeak - bPeak),
            meanAbsoluteSampleDifference = sumAbsoluteDifference / sampleCount,
            maxAbsoluteSampleDifference = maxAbsoluteDifference,
            changedFrameRatio = changedFrames.toDouble() / comparedFrames,
            nullDifferenceRms = sqrt(sumDifferenceSquared / sampleCount),
            spectralCentroidDeltaHz = spectrumB.centroidHz - spectrumA.centroidHz,
            lowBandEnergyDeltaDb = spectrumB.lowBandEnergyDb - spectrumA.lowBandEnergyDb,
            midBandEnergyDeltaDb = spectrumB.midBandEnergyDb - spectrumA.midBandEnergyDb,
            highBandEnergyDeltaDb = spectrumB.highBandEnergyDb - spectrumA.highBandEnergyDb
        )
    }

    fun renderJson(report: AudioComparisonReport): String = json.encodeToString(report)

    private fun validateFile(path: Path, label: String) {
        require(Files.isRegularFile(path)) { "$label input is not a regular file: $path" }
        require(Files.size(path) > 0) { "$label input is empty: $path" }
        require(path.fileName.toString().substringAfterLast('.', "").lowercase() in setOf("wav", "wave")) {
            "$label input must be a WAV file: $path"
        }
    }

    private fun validateBuffer(buffer: AudioBuffer, label: String) {
        require(buffer.format.sampleRate > 0) { "$label sample rate must be positive." }
        require(buffer.format.channels > 0) { "$label channel count must be positive." }
        require(buffer.samples.isNotEmpty() && buffer.samples.size % buffer.format.channels == 0) {
            "$label samples must contain complete, non-empty frames."
        }
        require(buffer.samples.all { it.isFinite() }) { "$label contains non-finite samples." }
    }

    private fun spectralMetrics(buffer: AudioBuffer, frameCount: Int): SpectralMetrics {
        val starts = if (frameCount <= FFT_WINDOW_SIZE) listOf(0) else {
            (0..frameCount - FFT_WINDOW_SIZE step FFT_HOP_SIZE).toList()
        }
        val power = DoubleArray(FFT_WINDOW_SIZE / 2 + 1)
        val window = DoubleArray(FFT_WINDOW_SIZE) { index ->
            0.5 - 0.5 * cos(2.0 * PI * index / (FFT_WINDOW_SIZE - 1))
        }
        for (start in starts) {
            for (channel in 0 until buffer.format.channels) {
                val real = DoubleArray(FFT_WINDOW_SIZE) { index ->
                    if (start + index < frameCount) buffer.samples[(start + index) * buffer.format.channels + channel] * window[index] else 0.0
                }
                val imaginary = DoubleArray(FFT_WINDOW_SIZE)
                fft(real, imaginary)
                for (bin in power.indices) power[bin] += real[bin] * real[bin] + imaginary[bin] * imaginary[bin]
            }
        }
        val normalization = (starts.size * buffer.format.channels * FFT_WINDOW_SIZE.toDouble() * FFT_WINDOW_SIZE).coerceAtLeast(1.0)
        power.indices.forEach { power[it] /= normalization }
        val binHz = buffer.format.sampleRate.toDouble() / FFT_WINDOW_SIZE
        var centroidWeight = 0.0
        var totalPower = 0.0
        var low = 0.0
        var mid = 0.0
        var high = 0.0
        for (bin in 1 until power.size) {
            val frequency = bin * binHz
            val value = power[bin]
            centroidWeight += frequency * value
            totalPower += value
            when {
                frequency < 250.0 -> low += value
                frequency < 4_000.0 -> mid += value
                else -> high += value
            }
        }
        return SpectralMetrics(
            windowSize = FFT_WINDOW_SIZE,
            hopSize = FFT_HOP_SIZE,
            analyzedWindows = starts.size,
            centroidHz = if (totalPower > 0.0) centroidWeight / totalPower else 0.0,
            lowBandEnergyDb = dbPower(low),
            midBandEnergyDb = dbPower(mid),
            highBandEnergyDb = dbPower(high)
        )
    }

    private fun fft(real: DoubleArray, imaginary: DoubleArray) {
        var j = 0
        for (i in 1 until real.size) {
            var bit = real.size shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val realValue = real[i]
                real[i] = real[j]
                real[j] = realValue
                val imaginaryValue = imaginary[i]
                imaginary[i] = imaginary[j]
                imaginary[j] = imaginaryValue
            }
        }
        var size = 2
        while (size <= real.size) {
            val angle = -2.0 * PI / size
            val stepReal = cos(angle)
            val stepImaginary = kotlin.math.sin(angle)
            for (start in real.indices step size) {
                var twiddleReal = 1.0
                var twiddleImaginary = 0.0
                for (offset in 0 until size / 2) {
                    val even = start + offset
                    val odd = even + size / 2
                    val transformedReal = real[odd] * twiddleReal - imaginary[odd] * twiddleImaginary
                    val transformedImaginary = real[odd] * twiddleImaginary + imaginary[odd] * twiddleReal
                    real[odd] = real[even] - transformedReal
                    imaginary[odd] = imaginary[even] - transformedImaginary
                    real[even] += transformedReal
                    imaginary[even] += transformedImaginary
                    val nextReal = twiddleReal * stepReal - twiddleImaginary * stepImaginary
                    twiddleImaginary = twiddleReal * stepImaginary + twiddleImaginary * stepReal
                    twiddleReal = nextReal
                }
            }
            size = size shl 1
        }
    }

    private fun db(value: Double): Double = if (value <= 0.0) DB_FLOOR else max(DB_FLOOR, 20.0 * log10(value))
    private fun dbPower(value: Double): Double = if (value <= 0.0) DB_FLOOR else max(DB_FLOOR, 10.0 * log10(value))
}

@Serializable
data class AudioComparisonReport(
    val tolerance: Double,
    val alignmentMode: Boolean,
    val alignmentMismatch: Boolean,
    val comparedFrameCount: Int,
    val frameCountDifference: Int,
    val durationDifferenceSeconds: Double,
    val a: SignalMetrics,
    val b: SignalMetrics,
    val rmsAbsoluteDelta: Double,
    val rmsDeltaDb: Double,
    val peakAbsoluteDelta: Double,
    val meanAbsoluteSampleDifference: Double,
    val maxAbsoluteSampleDifference: Double,
    val changedFrameRatio: Double,
    val nullDifferenceRms: Double,
    val spectralCentroidDeltaHz: Double,
    val lowBandEnergyDeltaDb: Double,
    val midBandEnergyDeltaDb: Double,
    val highBandEnergyDeltaDb: Double
)

@Serializable
data class SignalMetrics(
    val sampleRate: Int,
    val channels: Int,
    val frameCount: Int,
    val durationSeconds: Double,
    val rms: Double,
    val rmsDbFs: Double,
    val peak: Double,
    val spectrum: SpectralMetrics
)

@Serializable
data class SpectralMetrics(
    val windowSize: Int,
    val hopSize: Int,
    val analyzedWindows: Int,
    val centroidHz: Double,
    val lowBandEnergyDb: Double,
    val midBandEnergyDb: Double,
    val highBandEnergyDb: Double
)
