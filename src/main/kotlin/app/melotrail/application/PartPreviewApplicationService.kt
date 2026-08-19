package app.melotrail.application

import app.melotrail.arrangement.AnalysisKind
import app.melotrail.arrangement.InstrumentRenderer
import app.melotrail.arrangement.LogicalInstrument
import app.melotrail.arrangement.MidiAnalysis
import app.melotrail.arrangement.MidiQualityReportStore
import app.melotrail.arrangement.MidiFeelReportStore
import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.RenderFormat
import app.melotrail.arrangement.SelectedMidiArtifactResolver
import app.melotrail.preparation.InputInspectionPaths
import app.melotrail.preparation.InputInspectionReportStore
import app.melotrail.preparation.PreparationStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import javax.sound.sampled.AudioFormat as JavaAudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.math.roundToLong

/** A UI-neutral request for one monitor-only part artifact. */
data class PreviewRequest(
    val projectRoot: Path,
    val partId: String,
    val audioSource: PreviewAudioSource = PreviewAudioSource.ORIGINAL,
    /** Explicit A/B selection for the immutable raw and cleaned MIDI artifacts. */
    val midiSource: PreviewMidiSource? = null
)

/** Bounded monitor choices for a selected audio part; neither changes project release artifacts. */
enum class PreviewAudioSource { ORIGINAL, PREPARED_CLEAN }
enum class PreviewMidiSource { RAW, CLEANED, AI_FIX_DRAFT, AI_FIX_APPROVED, LOFI_FEEL }

enum class PreviewStage { VALIDATE, DECODE_OR_RENDER, VALIDATE_ARTIFACT, REUSE_OR_PUBLISH }

sealed interface PreviewResult {
    data class Resolved(val artifact: Path, val stages: List<PreviewStage>, val reused: Boolean) : PreviewResult
    data class Prerequisite(val stage: PreviewStage, val message: String) : PreviewResult
    data class Failed(val stage: PreviewStage, val message: String, val cause: Throwable? = null) : PreviewResult
}

/** MP3 is deliberately a replaceable local decoder boundary so normal tests stay offline. */
interface PreviewMp3Decoder {
    val configurationFingerprint: String
    suspend fun decode(source: Path, output: Path)
}

/** Uses installed Java Sound providers; unsupported MP3 providers produce a typed prerequisite. */
class JavaSoundPreviewMp3Decoder : PreviewMp3Decoder {
    override val configurationFingerprint: String = "java-sound-pcm24-v1"

    override suspend fun decode(source: Path, output: Path) = withContext(Dispatchers.IO) {
        try {
            AudioSystem.getAudioInputStream(source.toFile()).use { input ->
                val sourceFormat = input.format
                require(sourceFormat.sampleRate > 0 && sourceFormat.channels > 0) { "MP3 decoder did not report a usable audio format" }
                val pcm24 = JavaAudioFormat(
                    JavaAudioFormat.Encoding.PCM_SIGNED,
                    sourceFormat.sampleRate,
                    24,
                    sourceFormat.channels,
                    sourceFormat.channels * 3,
                    sourceFormat.sampleRate,
                    false
                )
                AudioSystem.getAudioInputStream(pcm24, input).use { converted ->
                    AudioSystem.write(converted, javax.sound.sampled.AudioFileFormat.Type.WAVE, output.toFile())
                }
            }
            Unit
        } catch (error: Exception) {
            throw IllegalStateException("MP3 decoding is unavailable or failed. Install a local Java Sound MP3 decoder and retry.", error)
        }
    }
}

/** Resolves validated monitor artifacts only; it neither starts playback nor touches release artifacts. */
interface PartPreviewApplicationService {
    suspend fun resolve(request: PreviewRequest): PreviewResult

    /** Compatibility seam for the existing transport adapter; Task 034 consumes [PreviewResult] directly. */
    suspend fun preview(root: Path, partId: String): Path = when (val result = resolve(PreviewRequest(root, partId))) {
        is PreviewResult.Resolved -> result.artifact
        is PreviewResult.Prerequisite -> throw IllegalStateException(result.message)
        is PreviewResult.Failed -> throw IllegalStateException(result.message, result.cause)
    }
}

class DefaultPartPreviewApplicationService(
    private val renderer: InstrumentRenderer,
    private val mp3Decoder: PreviewMp3Decoder = JavaSoundPreviewMp3Decoder(),
    private val rendererConfigurationFingerprint: () -> String = { renderer.javaClass.name }
) : PartPreviewApplicationService {
    override suspend fun resolve(request: PreviewRequest): PreviewResult = withContext(Dispatchers.IO) {
        val stages = mutableListOf(PreviewStage.VALIDATE)
        try {
            val root = request.projectRoot.toAbsolutePath().normalize()
            val project = ProjectStore.read(root).also { it.requireValid(root) }
            val part = project.parts.find { it.id == request.partId }
                ?: return@withContext PreviewResult.Failed(PreviewStage.VALIDATE, "Unknown part '${request.partId}'.")
            val source = root.resolve(part.file).normalize()
            if (!source.startsWith(root) || !Files.isRegularFile(source)) {
                return@withContext PreviewResult.Failed(PreviewStage.VALIDATE, "Part '${part.id}' source is missing or outside the project.")
            }

            if (request.audioSource == PreviewAudioSource.PREPARED_CLEAN) {
                if (extension(source) !in setOf("wav", "wave", "mp3")) {
                    return@withContext PreviewResult.Failed(PreviewStage.VALIDATE, "Prepared-audio preview is available only for WAV or MP3 source parts.")
                }
                return@withContext resolvePreparedClean(root, part.id, source, stages)
            }
            request.midiSource?.let { midiSource ->
                return@withContext resolveSelectedMidi(root, project, part, project.renderFormat, midiSource, stages)
            }
            when (extension(source)) {
                "wav", "wave" -> resolveWavSource(source, stages)
                "mp3" -> resolveMp3(root, part.id, source, stages)
                "mid", "midi" -> resolveMidi(root, project, part, stages)
                else -> PreviewResult.Failed(PreviewStage.VALIDATE, "Part '${part.id}' has an unsupported preview source format.")
            }
        } catch (error: IllegalArgumentException) {
            PreviewResult.Failed(PreviewStage.VALIDATE, error.message ?: "Preview input is invalid.", error)
        } catch (error: Exception) {
            PreviewResult.Failed(stages.lastOrNull() ?: PreviewStage.VALIDATE, error.message ?: "Preview resolution failed.", error)
        }
    }

    private fun resolveWavSource(source: Path, stages: MutableList<PreviewStage>): PreviewResult {
        val info = inspectWav(source) ?: return PreviewResult.Failed(PreviewStage.VALIDATE, "WAV source is not a supported RIFF PCM/float WAV.")
        if (info.frames <= 0) return PreviewResult.Failed(PreviewStage.VALIDATE, "WAV source contains no audio frames.")
        stages += PreviewStage.VALIDATE_ARTIFACT
        return PreviewResult.Resolved(source, stages + PreviewStage.REUSE_OR_PUBLISH, reused = true)
    }

    private fun resolvePreparedClean(root: Path, partId: String, source: Path, stages: MutableList<PreviewStage>): PreviewResult {
        val report = runCatching { InputInspectionReportStore.read(root, partId) }.getOrNull()
            ?: return PreviewResult.Prerequisite(PreviewStage.VALIDATE, "Inspect the original source before previewing prepared audio.")
        val cleanup = report.cleanup?.output
        if (report.preparation != PreparationStatus.CLEANED || cleanup == null || report.source.sha256 != digest(Files.readAllBytes(source))) {
            return PreviewResult.Prerequisite(PreviewStage.VALIDATE, "Prepared audio is unavailable or stale. Inspect and apply the current cleanup recommendation again.")
        }
        val clean = InputInspectionPaths.cleanWav(root, partId)
        if (!clean.startsWith(root) || !Files.isRegularFile(clean) || cleanup.sha256 != digest(Files.readAllBytes(clean))) {
            return PreviewResult.Prerequisite(PreviewStage.VALIDATE, "Prepared audio is unavailable or stale. Inspect and apply the current cleanup recommendation again.")
        }
        val info = inspectWav(clean) ?: return PreviewResult.Failed(PreviewStage.VALIDATE, "Prepared audio is not a supported RIFF PCM WAV.")
        if (info.frames <= 0) return PreviewResult.Failed(PreviewStage.VALIDATE, "Prepared audio contains no audio frames.")
        stages += PreviewStage.VALIDATE_ARTIFACT
        return PreviewResult.Resolved(clean, stages + PreviewStage.REUSE_OR_PUBLISH, reused = true)
    }

    private suspend fun resolveMp3(root: Path, partId: String, source: Path, stages: MutableList<PreviewStage>): PreviewResult {
        val target = previewTarget(root, "audio", partId, fingerprint(source, mp3Decoder.configurationFingerprint))
        stages += PreviewStage.DECODE_OR_RENDER
        if (validMonitorWav(target, null, null, null)) return PreviewResult.Resolved(target, stages + PreviewStage.VALIDATE_ARTIFACT + PreviewStage.REUSE_OR_PUBLISH, true)
        return try {
            val published = publish(target, { temporary -> mp3Decoder.decode(source, temporary) }) { temporary -> validMonitorWav(temporary, null, null, 24) }
            if (!published) {
                PreviewResult.Failed(PreviewStage.VALIDATE_ARTIFACT, "MP3 decoder did not produce a valid PCM-24 WAV monitor artifact.")
            } else PreviewResult.Resolved(target, stages + PreviewStage.VALIDATE_ARTIFACT + PreviewStage.REUSE_OR_PUBLISH, false)
        } catch (error: Exception) {
            PreviewResult.Prerequisite(PreviewStage.DECODE_OR_RENDER, error.message ?: "MP3 decoding is unavailable.")
        }
    }

    private suspend fun resolveMidi(root: Path, project: app.melotrail.arrangement.Project, part: app.melotrail.arrangement.SongPart, stages: MutableList<PreviewStage>): PreviewResult {
        val selected = try { SelectedMidiArtifactResolver().resolve(root, project, part) } catch (error: IllegalArgumentException) {
            return PreviewResult.Prerequisite(PreviewStage.VALIDATE, error.message ?: "Selected MIDI is unavailable for '${part.id}'.")
        }
        val partId = part.id
        val analysisRef = part.analysis
        val format = project.renderFormat
        if (format == null) return PreviewResult.Prerequisite(PreviewStage.VALIDATE, "Project render format is required before previewing MIDI.")
        if (analysisRef == null || analysisRef.kind != AnalysisKind.MIDI) return PreviewResult.Prerequisite(PreviewStage.VALIDATE, "Analyze '$partId' before previewing its selected MIDI.")
        val clean = selected.path
        val analysisPath = root.resolve(analysisRef.file).normalize()
        if (!clean.startsWith(root) || !analysisPath.startsWith(root) || !Files.isRegularFile(clean) || !Files.isRegularFile(analysisPath)) {
            return PreviewResult.Prerequisite(PreviewStage.VALIDATE, "Selected MIDI analysis for '$partId' is missing.")
        }
        val duration = try { json.decodeFromString(MidiAnalysis.serializer(), Files.readString(analysisPath)).durationSeconds } catch (error: Exception) {
            return PreviewResult.Prerequisite(PreviewStage.VALIDATE, "MIDI analysis for '$partId' is invalid: ${error.message ?: "unreadable"}")
        }
        if (!duration.isFinite() || duration <= 0) return PreviewResult.Prerequisite(PreviewStage.VALIDATE, "MIDI analysis for '$partId' has no usable duration.")
        val frames = (duration * format.sampleRate).roundToLong()
        val target = previewTarget(root, "piano", partId, fingerprint(clean, "${format.sampleRate}|${format.channels}|$frames|${rendererConfigurationFingerprint()}"))
        stages += PreviewStage.DECODE_OR_RENDER
        if (validMonitorWav(target, format.sampleRate, format.channels, 24, frames)) return PreviewResult.Resolved(target, stages + PreviewStage.VALIDATE_ARTIFACT + PreviewStage.REUSE_OR_PUBLISH, true)
        return try {
            val published = publish(target, { temporary -> renderer.render(clean, LogicalInstrument.PIANO, temporary, format, frames) }) { temporary -> validMonitorWav(temporary, format.sampleRate, format.channels, 24, frames) }
            if (!published) {
                PreviewResult.Failed(PreviewStage.VALIDATE_ARTIFACT, "Piano renderer did not produce a valid project-format WAV monitor artifact.")
            } else PreviewResult.Resolved(target, stages + PreviewStage.VALIDATE_ARTIFACT + PreviewStage.REUSE_OR_PUBLISH, false)
        } catch (error: Exception) {
            PreviewResult.Prerequisite(PreviewStage.DECODE_OR_RENDER, "Piano preview renderer or sound library is unavailable: ${error.message ?: "unknown failure"}")
        }
    }

    private suspend fun resolveSelectedMidi(
        root: Path,
        project: app.melotrail.arrangement.Project,
        part: app.melotrail.arrangement.SongPart,
        format: RenderFormat?,
        source: PreviewMidiSource,
        stages: MutableList<PreviewStage>
    ): PreviewResult {
        val midi = part.midi ?: return PreviewResult.Prerequisite(PreviewStage.VALIDATE, "Raw MIDI is not available for '${part.id}'.")
        val reference = when (source) {
            PreviewMidiSource.RAW -> midi.raw
            PreviewMidiSource.CLEANED -> midi.clean
            PreviewMidiSource.AI_FIX_DRAFT -> midi.aiFix?.draft?.file
            PreviewMidiSource.AI_FIX_APPROVED -> midi.aiFix?.approved?.file
            PreviewMidiSource.LOFI_FEEL -> midi.feel?.derived
        } ?: return PreviewResult.Prerequisite(PreviewStage.VALIDATE, "${source.name.lowercase().replaceFirstChar(Char::uppercase)} MIDI is not available for '${part.id}'.")
        if (source == PreviewMidiSource.CLEANED && midi.raw != null) {
            val current = midi.cleanup != null && midi.quality != null && MidiQualityReportStore.isCurrent(root, part.id, midi.raw, reference, midi.cleanup, midi.quality)
            if (!current) return PreviewResult.Prerequisite(PreviewStage.VALIDATE, "Cleaned MIDI quality evidence is missing or stale. Run Clean MIDI again.")
        }
        if (source == PreviewMidiSource.LOFI_FEEL) {
            val feel = midi.feel
            val base = runCatching {
                val baseProject = project.copy(parts = project.parts.map {
                    if (it.id == part.id) it.copy(midi = midi.copy(analysisInput = app.melotrail.arrangement.MidiAnalysisInput.CURRENT)) else it
                })
                SelectedMidiArtifactResolver().resolve(root, baseProject, part.id)
            }.getOrElse { error ->
                return PreviewResult.Prerequisite(PreviewStage.VALIDATE, error.message ?: "Current MIDI base is unavailable for '${part.id}'.")
            }
            if (feel == null || !MidiFeelReportStore.isCurrent(root, part.id, base.projectRelativePath, feel)) {
                return PreviewResult.Prerequisite(PreviewStage.VALIDATE, "Lo-fi Feel MIDI is unavailable or stale. Choose Original feel or regenerate the fixed profile.")
            }
        }
        if (source == PreviewMidiSource.AI_FIX_DRAFT || source == PreviewMidiSource.AI_FIX_APPROVED) {
            val fix = midi.aiFix
            val clean = midi.clean
            val artifact = if (source == PreviewMidiSource.AI_FIX_DRAFT) fix?.draft else fix?.approved
            if (clean == null || fix == null || artifact == null || fix.inputSha256 != digest(Files.readAllBytes(root.resolve(clean))) || artifact.sha256 != digest(Files.readAllBytes(root.resolve(artifact.file)))) {
                return PreviewResult.Prerequisite(PreviewStage.VALIDATE, "AI-fix MIDI is unavailable or stale. Keep cleaned MIDI or regenerate the AI fix.")
            }
        }
        if (format == null) return PreviewResult.Prerequisite(PreviewStage.VALIDATE, "Project render format is required before previewing MIDI.")
        val midiPath = root.resolve(reference).normalize()
        if (!midiPath.startsWith(root) || !Files.isRegularFile(midiPath)) return PreviewResult.Prerequisite(PreviewStage.VALIDATE, "Selected MIDI is missing for '${part.id}'.")
        val duration = try { javax.sound.midi.MidiSystem.getSequence(midiPath.toFile()).microsecondLength / 1_000_000.0 } catch (_: Exception) { 0.0 }
        if (!duration.isFinite() || duration <= 0.0) return PreviewResult.Prerequisite(PreviewStage.VALIDATE, "Selected MIDI has no usable duration.")
        val frames = (duration * format.sampleRate).roundToLong()
        val target = previewTarget(root, "piano-${source.name.lowercase()}", part.id, fingerprint(midiPath, "${format.sampleRate}|${format.channels}|$frames|${rendererConfigurationFingerprint()}"))
        stages += PreviewStage.DECODE_OR_RENDER
        if (validMonitorWav(target, format.sampleRate, format.channels, 24, frames)) return PreviewResult.Resolved(target, stages + PreviewStage.VALIDATE_ARTIFACT + PreviewStage.REUSE_OR_PUBLISH, true)
        return try {
            val published = publish(target, { temporary -> renderer.render(midiPath, LogicalInstrument.PIANO, temporary, format, frames) }) { temporary -> validMonitorWav(temporary, format.sampleRate, format.channels, 24, frames) }
            if (!published) PreviewResult.Failed(PreviewStage.VALIDATE_ARTIFACT, "Piano renderer did not produce a valid project-format WAV monitor artifact.")
            else PreviewResult.Resolved(target, stages + PreviewStage.VALIDATE_ARTIFACT + PreviewStage.REUSE_OR_PUBLISH, false)
        } catch (error: Exception) {
            PreviewResult.Prerequisite(PreviewStage.DECODE_OR_RENDER, "Piano preview renderer or sound library is unavailable: ${error.message ?: "unknown failure"}")
        }
    }

    private suspend fun publish(target: Path, action: suspend (Path) -> Unit, valid: (Path) -> Boolean): Boolean {
        Files.createDirectories(target.parent)
        val temporary = target.resolveSibling(".${target.fileName}.${UUID.randomUUID()}.tmp.wav")
        try {
            action(temporary)
            if (!valid(temporary)) return false
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
            }
            return true
        } finally { Files.deleteIfExists(temporary) }
    }

    private fun previewTarget(root: Path, kind: String, partId: String, fingerprint: String): Path {
        require(PART_ID.matches(partId)) { "Part ID is invalid." }
        return root.resolve("previews/$kind-$partId-$fingerprint.wav").normalize().also { require(it.startsWith(root)) }
    }

    private fun fingerprint(source: Path, configuration: String): String = digest(Files.readAllBytes(source) + configuration.toByteArray())
    private fun validMonitorWav(path: Path, rate: Int?, channels: Int?, bitDepth: Int?, frames: Long? = null): Boolean {
        val info = inspectWav(path) ?: return false
        return info.frames > 0 && (rate == null || info.sampleRate == rate) && (channels == null || info.channels == channels) &&
            (bitDepth == null || info.bitDepth == bitDepth) && (frames == null || info.frames == frames)
    }
    private fun extension(path: Path): String = path.fileName.toString().substringAfterLast('.', "").lowercase()
    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private data class WavInfo(val sampleRate: Int, val channels: Int, val bitDepth: Int, val frames: Long)
    private fun inspectWav(path: Path): WavInfo? = try {
        java.io.RandomAccessFile(path.toFile(), "r").use { file ->
            if (file.length() < 44 || file.readFourCc() != "RIFF") return null
            file.skipBytes(4); if (file.readFourCc() != "WAVE") return null
            var rate = 0; var channels = 0; var bits = 0; var bytesPerFrame = 0; var data = -1L
            while (file.filePointer + 8 <= file.length()) {
                val id = file.readFourCc(); val size = file.readUInt32()
                if (size > file.length() - file.filePointer) return null
                when (id) {
                    "fmt " -> { if (size < 16) return null; val codec = file.readUInt16(); channels = file.readUInt16(); rate = file.readUInt32().toInt(); file.skipBytes(6); bytesPerFrame = file.readUInt16(); bits = file.readUInt16(); if (codec !in setOf(1, 3)) return null; file.seek(file.filePointer + size - 16) }
                    "data" -> { data = size; file.seek(file.filePointer + size) }
                    else -> file.seek(file.filePointer + size)
                }
                if (size % 2L == 1L && file.filePointer < file.length()) file.skipBytes(1)
            }
            if (rate <= 0 || channels !in 1..32 || bits !in setOf(8, 16, 24, 32) || bytesPerFrame <= 0 || data < 0 || data % bytesPerFrame != 0L) null else WavInfo(rate, channels, bits, data / bytesPerFrame)
        }
    } catch (_: Exception) { null }

    private companion object { val json = Json { ignoreUnknownKeys = false }; val PART_ID = Regex("[A-Za-z0-9_-]+") }
}

private fun java.io.RandomAccessFile.readFourCc(): String = ByteArray(4).also { readFully(it) }.toString(Charsets.US_ASCII)
private fun java.io.RandomAccessFile.readUInt16(): Int = read() or (read() shl 8)
private fun java.io.RandomAccessFile.readUInt32(): Long = readUInt16().toLong() or (readUInt16().toLong() shl 16)
