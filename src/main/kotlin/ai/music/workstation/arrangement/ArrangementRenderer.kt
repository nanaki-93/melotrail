package ai.music.workstation.arrangement

import ai.music.workstation.audio.AudioBuffer
import ai.music.workstation.audio.AudioFormat
import ai.music.workstation.audio.AudioResampler
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Turns a validated arrangement plan into aligned local stems. It intentionally
 * synthesizes only small, deterministic musical gestures; Qwen may select a
 * gesture but never supplies samples, code, or unrestricted note data.
 */
class ArrangementRenderer {
    data class RenderedArrangement(
        val tracks: List<MixTrack>,
        val sampleRate: Int,
        val channels: Int,
        val frameCount: Int,
        val boundaries: List<RenderedBoundary>
    )

    data class RenderedBoundary(
        val index: Int,
        val startFrame: Int,
        val endFrame: Int,
        val description: String
    )

    fun render(
        arrangement: Arrangement,
        sources: List<AudioBuffer>,
        analyses: Map<String, PartAnalysis>
    ): RenderedArrangement {
        require(sources.size == arrangement.sections.size) { "Source count must match arrangement sections" }
        require(sources.isNotEmpty()) { "Arrangement must contain at least one section" }
        val sampleRate = sources.first().format.sampleRate
        val channels = sources.maxOf { it.format.channels }
        require(channels in 1..2) { "Renderer supports mono and stereo sources only" }
        val prepared = sources.map { convertChannels(AudioResampler.resample(it, sampleRate), channels) }
        val starts = IntArray(prepared.size)
        val fadeIns = IntArray(prepared.size)
        val fadeOuts = IntArray(prepared.size)
        val bridges = mutableListOf<RenderedBoundary>()
        var cursor = 0

        prepared.indices.forEach { index ->
            starts[index] = cursor
            val sourceEnd = Math.addExact(cursor, prepared[index].length)
            val transition = arrangement.sections[index].transitionOut
            if (index == prepared.lastIndex) {
                cursor = sourceEnd
                return@forEach
            }
            when (transition.type) {
                TransitionType.NONE -> cursor = sourceEnd
                TransitionType.CROSSFADE -> {
                    val requested = msToFrames(transition.crossfadeMs, sampleRate)
                    val overlap = min(requested, min(prepared[index].length / 2, prepared[index + 1].length / 2))
                    fadeOuts[index] = overlap
                    fadeIns[index + 1] = overlap
                    cursor = sourceEnd - overlap
                    bridges += RenderedBoundary(index, cursor, sourceEnd, "${transition.crossfadeMs}ms crossfade")
                }
                TransitionType.BRIDGE -> {
                    val fade = min(msToFrames(max(transition.crossfadeMs, DEFAULT_BRIDGE_FADE_MS), sampleRate), prepared[index].length / 2)
                    fadeOuts[index] = fade
                    fadeIns[index + 1] = min(fade, prepared[index + 1].length / 2)
                    val bpm = analyses[arrangement.sections[index].partId]?.bpm ?: DEFAULT_BPM
                    val bridgeFrames = (transition.bars * 4.0 * 60.0 / bpm * sampleRate).toInt()
                    bridges += RenderedBoundary(index, sourceEnd, sourceEnd + bridgeFrames, "${transition.bars}-bar bridge")
                    cursor = sourceEnd + bridgeFrames
                }
            }
        }

        val frameCount = cursor
        val tracks = mutableListOf<MixTrack>()
        prepared.forEachIndexed { index, source ->
            tracks += MixTrack(
                name = "source-${arrangement.sections[index].index}-${arrangement.sections[index].partId}",
                buffer = fade(source, fadeIns[index], fadeOuts[index]),
                startFrame = starts[index]
            )
        }
        val format = AudioFormat(sampleRate, channels, 24, false, false, "WAV")
        val stemSamples = linkedMapOf("drums" to FloatArray(frameCount * channels), "pad" to FloatArray(frameCount * channels), "bridges" to FloatArray(frameCount * channels))
        arrangement.sections.forEachIndexed { index, section ->
            val sectionStart = starts[index]
            val sectionFrames = prepared[index].length
            val energy = section.instruments.filter { it.mode == InstrumentMode.GENERATED }.maxOfOrNull { it.density ?: 0.0 } ?: 0.0
            section.instruments.filter { it.mode == InstrumentMode.GENERATED }.forEach { instrument ->
                when (instrument.name.lowercase()) {
                    "drums" -> renderDrums(stemSamples.getValue("drums"), sectionStart, sectionFrames, format, instrument.density ?: 0.0)
                    "pad", "pads" -> renderPad(stemSamples.getValue("pad"), sectionStart, sectionFrames, format, instrument.density ?: 0.0)
                }
            }
            if (index < arrangement.sections.lastIndex && section.transitionOut.type == TransitionType.BRIDGE) {
                val boundary = bridges.first { it.index == index }
                renderBridge(stemSamples.getValue("bridges"), boundary, format, section.transitionOut.bridge!!, energy)
            }
        }
        stemSamples.forEach { (name, samples) ->
            if (samples.any { it != 0f }) tracks += MixTrack(name, AudioBuffer(samples, format, frameCount.toDouble() / sampleRate), generated = true)
        }
        return RenderedArrangement(tracks, sampleRate, channels, frameCount, bridges)
    }

    private fun renderDrums(output: FloatArray, start: Int, length: Int, format: AudioFormat, density: Double) {
        val beat = (format.sampleRate * 60.0 / DEFAULT_BPM).toInt()
        for (frame in 0 until length step max(1, beat)) {
            addKick(output, start + frame, min(format.sampleRate / 7, length - frame), format, 0.13 * density)
            if ((frame / beat) % 2 == 1) addNoise(output, start + frame, min(format.sampleRate / 12, length - frame), format, 0.06 * density)
            addNoise(output, start + frame + beat / 2, min(format.sampleRate / 45, max(0, length - frame - beat / 2)), format, 0.025 * density)
        }
    }

    private fun renderPad(output: FloatArray, start: Int, length: Int, format: AudioFormat, density: Double) {
        listOf(220.0, 261.63, 329.63).forEachIndexed { index, frequency ->
            addTone(output, start, length, format, frequency, (0.018 * density) / (index + 1), 600)
        }
    }

    private fun renderBridge(output: FloatArray, boundary: RenderedBoundary, format: AudioFormat, bridge: BridgePlan, fallbackEnergy: Double) {
        val energy = bridge.energy.coerceIn(0.0, 1.0).takeIf { it > 0.0 } ?: fallbackEnergy
        val length = boundary.endFrame - boundary.startFrame
        bridge.elements.forEach { element -> when (element) {
            BridgeElement.PAD_SWELL -> renderPad(output, boundary.startFrame, length, format, energy * 1.4)
            BridgeElement.DRUM_FILL -> renderDrums(output, boundary.startFrame, length, format, energy * 1.2)
            BridgeElement.BASS_PICKUP -> addTone(output, boundary.startFrame + length / 2, length / 2, format, 55.0, 0.11 * energy, 20)
            BridgeElement.MELODY_PICKUP -> listOf(440.0, 523.25, 659.25).forEachIndexed { i, hz ->
                addTone(output, boundary.startFrame + i * length / 3, length / 4, format, hz, 0.035 * energy, 25)
            }
        }}
    }

    private fun addTone(output: FloatArray, start: Int, length: Int, format: AudioFormat, hz: Double, amplitude: Double, fadeMs: Int) {
        val fadeFrames = min(length / 2, msToFrames(fadeMs, format.sampleRate))
        for (frame in 0 until max(0, length)) {
            val env = when {
                fadeFrames == 0 -> 1.0
                frame < fadeFrames -> frame.toDouble() / fadeFrames
                frame >= length - fadeFrames -> (length - frame - 1).toDouble() / fadeFrames
                else -> 1.0
            }.coerceAtLeast(0.0)
            val value = (sin(2.0 * PI * hz * frame / format.sampleRate) * amplitude * env).toFloat()
            add(output, start + frame, format.channels, value)
        }
    }

    private fun addKick(output: FloatArray, start: Int, length: Int, format: AudioFormat, amplitude: Double) {
        for (frame in 0 until max(0, length)) {
            val envelope = (1.0 - frame.toDouble() / max(1, length)).let { it * it }
            val hz = 120.0 - 70.0 * frame / max(1, length).toDouble()
            add(output, start + frame, format.channels, (sin(2 * PI * hz * frame / format.sampleRate) * amplitude * envelope).toFloat())
        }
    }

    private fun addNoise(output: FloatArray, start: Int, length: Int, format: AudioFormat, amplitude: Double) {
        for (frame in 0 until max(0, length)) {
            val pseudoNoise = (((frame * 1103515245L + 12345L) ushr 16) and 0x7fff).toDouble() / 16384.0 - 1.0
            add(output, start + frame, format.channels, (pseudoNoise * amplitude * (1.0 - frame.toDouble() / max(1, length))).toFloat())
        }
    }

    private fun add(output: FloatArray, frame: Int, channels: Int, value: Float) {
        if (frame < 0 || frame * channels >= output.size) return
        repeat(channels) { channel -> output[frame * channels + channel] += value }
    }

    private fun fade(buffer: AudioBuffer, inFrames: Int, outFrames: Int): AudioBuffer {
        if (inFrames == 0 && outFrames == 0) return buffer
        val samples = buffer.samples.clone()
        for (frame in 0 until buffer.length) {
            val gain = when {
                inFrames > 0 && frame < inFrames -> sin(PI * frame / (2.0 * inFrames))
                outFrames > 0 && frame >= buffer.length - outFrames -> cos(PI * (frame - (buffer.length - outFrames)) / (2.0 * outFrames))
                else -> 1.0
            }.toFloat()
            repeat(buffer.format.channels) { channel -> samples[frame * buffer.format.channels + channel] *= gain }
        }
        return buffer.copy(samples = samples)
    }

    private fun convertChannels(buffer: AudioBuffer, channels: Int): AudioBuffer {
        if (buffer.format.channels == channels) return buffer
        val samples = FloatArray(buffer.length * channels)
        for (frame in 0 until buffer.length) repeat(channels) { channel -> samples[frame * channels + channel] = buffer.getSample(0, frame) }
        return buffer.copy(samples = samples, format = buffer.format.copy(channels = channels))
    }

    private fun msToFrames(milliseconds: Int, sampleRate: Int): Int = (milliseconds / 1000.0 * sampleRate).toInt()

    private companion object {
        const val DEFAULT_BPM = 80.0
        const val DEFAULT_BRIDGE_FADE_MS = 180
        const val ATTACK_RELEASE_MS = 45
    }
}
