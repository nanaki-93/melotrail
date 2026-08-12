package ai.music.workstation.arrangement

import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/** A minimal note event passed from arrangement generation to the local renderer. */
data class BassNote(
    val startFrame: Int,
    val durationFrames: Int,
    val midiNote: Int = DEFAULT_BASS_MIDI_NOTE,
    val velocity: Double
) {
    companion object {
        const val DEFAULT_BASS_MIDI_NOTE = 36
    }
}

data class BassRenderRequest(
    val notes: List<BassNote>,
    val sampleRate: Int,
    val channels: Int,
    val frameCount: Int
)

/** Local rendering boundary. A future real instrument renderer can replace the test renderer. */
fun interface BassStemRenderer {
    fun render(request: BassRenderRequest, outputPath: Path)
}

/** Result metadata for one generated lossless WAV stem. */
data class GeneratedBassStem(
    val path: Path,
    val sampleRate: Int,
    val channels: Int,
    val frameCount: Int,
    val notes: List<BassNote>
)

interface InstrumentStemGenerator {
    fun generate(
        projectRoot: Path,
        project: Project,
        arrangement: Arrangement,
        analyses: Map<String, PartAnalysis>
    ): GeneratedBassStem
}

/**
 * First generation spike: converts generated bass plans into note events and invokes
 * a deliberately small local renderer. It never reads or changes source part files.
 */
class BassStemGenerationAdapter(
    private val renderer: BassStemRenderer = DeterministicTestBassRenderer()
) : InstrumentStemGenerator {
    override fun generate(
        projectRoot: Path,
        project: Project,
        arrangement: Arrangement,
        analyses: Map<String, PartAnalysis>
    ): GeneratedBassStem {
        val root = projectRoot.toAbsolutePath().normalize()
        project.requireValid(root)
        arrangement.requireValid(project.parts.map { it.id })

        val timeline = buildTimeline(arrangement, analyses)
        require(timeline.notes.isNotEmpty()) {
            "Arrangement does not contain a generated bass instrument"
        }
        require(timeline.frameCount <= Int.MAX_VALUE / timeline.channels) {
            "Generated bass stem is too large to render in memory"
        }

        val frameCount = timeline.frameCount.toInt()
        val outputPath = root.resolve(STEMS_DIRECTORY).resolve(BASS_FILE_NAME)
        renderer.render(
            BassRenderRequest(
                notes = timeline.notes,
                sampleRate = timeline.sampleRate,
                channels = timeline.channels,
                frameCount = frameCount
            ),
            outputPath
        )
        return GeneratedBassStem(
            path = outputPath,
            sampleRate = timeline.sampleRate,
            channels = timeline.channels,
            frameCount = frameCount,
            notes = timeline.notes
        )
    }

    private fun buildTimeline(
        arrangement: Arrangement,
        analyses: Map<String, PartAnalysis>
    ): BassTimeline {
        var sampleRate: Int? = null
        var channels: Int? = null
        var startFrame = 0L
        val notes = mutableListOf<BassNote>()

        arrangement.sections.forEachIndexed { position, section ->
            val analysis = analyses[section.partId]
                ?: throw IllegalArgumentException("Missing analysis for arranged part '${section.partId}'")
            requireValidAnalysis(section.partId, analysis)
            if (sampleRate == null) {
                sampleRate = analysis.sampleRate
                channels = analysis.channels
            } else {
                require(sampleRate == analysis.sampleRate && channels == analysis.channels) {
                    "All arranged parts must have the same sample rate and channels for this bass spike"
                }
            }

            val bass = section.instruments.firstOrNull {
                it.mode == InstrumentMode.GENERATED && it.name.equals(BASS_INSTRUMENT_NAME, ignoreCase = true)
            }
            if (bass != null && bass.density!! > 0.0) {
                require(startFrame <= Int.MAX_VALUE && analysis.frameCount <= Int.MAX_VALUE) {
                    "Arrangement section ${position + 1} is too long to render"
                }
                notes += BassNote(
                    startFrame = startFrame.toInt(),
                    durationFrames = analysis.frameCount.toInt(),
                    velocity = bass.density
                )
            }
            startFrame = Math.addExact(startFrame, analysis.frameCount)
        }

        return BassTimeline(
            notes = notes,
            sampleRate = checkNotNull(sampleRate),
            channels = checkNotNull(channels),
            frameCount = startFrame
        )
    }

    private fun requireValidAnalysis(partId: String, analysis: PartAnalysis) {
        require(analysis.sampleRate > 0) { "Analysis for part '$partId' has an invalid sample rate" }
        require(analysis.channels > 0) { "Analysis for part '$partId' has an invalid channel count" }
        require(analysis.frameCount > 0) { "Analysis for part '$partId' has no audio frames" }
        require(analysis.duration.isFinite() && analysis.duration > 0.0) {
            "Analysis for part '$partId' has an invalid duration"
        }
    }

    private data class BassTimeline(
        val notes: List<BassNote>,
        val sampleRate: Int,
        val channels: Int,
        val frameCount: Long
    )

    private companion object {
        const val STEMS_DIRECTORY = "stems"
        const val BASS_FILE_NAME = "bass.wav"
        const val BASS_INSTRUMENT_NAME = "bass"
    }
}

/**
 * Deterministic placeholder renderer for the spike, not a general synthesizer.
 * It writes PCM_24 WAV, matching the project's lossless intermediate convention.
 */
class DeterministicTestBassRenderer : BassStemRenderer {
    override fun render(request: BassRenderRequest, outputPath: Path) {
        require(request.sampleRate > 0) { "Sample rate must be positive" }
        require(request.channels > 0) { "Channel count must be positive" }
        require(request.frameCount > 0) { "Frame count must be positive" }
        require(request.frameCount <= Int.MAX_VALUE / request.channels) { "Render request is too large" }
        request.notes.forEach { note ->
            require(note.startFrame >= 0 && note.durationFrames > 0) { "Bass note frames must be positive" }
            require(note.startFrame.toLong() + note.durationFrames <= request.frameCount) {
                "Bass note exceeds the rendered timeline"
            }
            require(note.velocity.isFinite() && note.velocity in 0.0..1.0) {
                "Bass note velocity must be between 0 and 1"
            }
        }

        val samples = FloatArray(request.frameCount * request.channels)
        request.notes.forEach { note -> renderNote(samples, request, note) }
        writePcm24Wav(samples, request.sampleRate, request.channels, outputPath)
    }

    private fun renderNote(samples: FloatArray, request: BassRenderRequest, note: BassNote) {
        val frequency = 440.0 * 2.0.pow((note.midiNote - 69) / 12.0)
        val fadeFrames = minOf(MAX_FADE_FRAMES, note.durationFrames / 2)
        for (offset in 0 until note.durationFrames) {
            val envelope = when {
                fadeFrames == 0 -> 1.0
                offset < fadeFrames -> offset.toDouble() / fadeFrames
                offset >= note.durationFrames - fadeFrames ->
                    (note.durationFrames - offset - 1).toDouble() / fadeFrames
                else -> 1.0
            }
            val sample = (
                sin(2.0 * PI * frequency * offset / request.sampleRate) *
                    MAX_AMPLITUDE * note.velocity * envelope
                ).toFloat()
            val frame = note.startFrame + offset
            repeat(request.channels) { channel ->
                samples[frame * request.channels + channel] += sample
            }
        }
    }

    private fun writePcm24Wav(samples: FloatArray, sampleRate: Int, channels: Int, outputPath: Path) {
        val dataSize = Math.multiplyExact(samples.size, BYTES_PER_SAMPLE)
        val byteRate = Math.multiplyExact(sampleRate, Math.multiplyExact(channels, BYTES_PER_SAMPLE))
        Files.createDirectories(checkNotNull(outputPath.parent))
        DataOutputStream(BufferedOutputStream(FileOutputStream(outputPath.toFile()))).use { output ->
            output.writeBytes("RIFF")
            output.writeLittleEndianInt(36 + dataSize)
            output.writeBytes("WAVE")
            output.writeBytes("fmt ")
            output.writeLittleEndianInt(16)
            output.writeLittleEndianShort(PCM_FORMAT)
            output.writeLittleEndianShort(channels)
            output.writeLittleEndianInt(sampleRate)
            output.writeLittleEndianInt(byteRate)
            output.writeLittleEndianShort(channels * BYTES_PER_SAMPLE)
            output.writeLittleEndianShort(PCM_BIT_DEPTH)
            output.writeBytes("data")
            output.writeLittleEndianInt(dataSize)
            samples.forEach { sample ->
                val value = (sample.coerceIn(-1f, 1f) * PCM_24_MAX).toInt()
                output.writeByte(value and 0xFF)
                output.writeByte((value ushr 8) and 0xFF)
                output.writeByte((value ushr 16) and 0xFF)
            }
        }
    }

    private fun DataOutputStream.writeLittleEndianInt(value: Int) {
        writeByte(value and 0xFF)
        writeByte((value ushr 8) and 0xFF)
        writeByte((value ushr 16) and 0xFF)
        writeByte((value ushr 24) and 0xFF)
    }

    private fun DataOutputStream.writeLittleEndianShort(value: Int) {
        writeByte(value and 0xFF)
        writeByte((value ushr 8) and 0xFF)
    }

    private companion object {
        const val BYTES_PER_SAMPLE = 3
        const val PCM_BIT_DEPTH = 24
        const val PCM_FORMAT = 1
        const val PCM_24_MAX = 8_388_607f
        const val MAX_AMPLITUDE = 0.15
        const val MAX_FADE_FRAMES = 240
    }
}
