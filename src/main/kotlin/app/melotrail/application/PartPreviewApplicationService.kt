package app.melotrail.application

import app.melotrail.arrangement.AnalysisKind
import app.melotrail.arrangement.MelodyConnectionPlanner
import app.melotrail.arrangement.InstrumentRenderer
import app.melotrail.arrangement.LogicalInstrument
import app.melotrail.arrangement.MidiAnalysis
import app.melotrail.arrangement.MidiQualityReportStore
import app.melotrail.arrangement.MidiFeelReportStore
import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.RenderFormat
import app.melotrail.arrangement.SelectedMidiArtifactResolver
import app.melotrail.arrangement.SourceSong
import app.melotrail.arrangement.WorkflowArtifactReference
import app.melotrail.arrangement.DeterministicStemMixer
import app.melotrail.arrangement.MixedStem
import app.melotrail.audio.WAVDecoder
import app.melotrail.model.ErrorReporter
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
import javax.sound.midi.MetaMessage
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiMessage
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.sampled.AudioFormat as JavaAudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.math.abs
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
/**
 * A bounded representation selector for monitor rendering.  These are not
 * paths and do not alter the project's selected downstream artifact.
 */
enum class PreviewMidiSource { RAW, CLEANED, CORRECTED, ENHANCED, AI_FIX_DRAFT, AI_FIX_APPROVED, LOFI_FEEL }

/** Bounded canonical melody monitor choices; none selects, mutates, or publishes release MIDI. */
enum class SourceSongReviewPreview { SOURCE, PREPARED, FULL_MELODY, BOUNDARY }

/** A UI-neutral canonical-melody monitor request with stable part/boundary identities only. */
data class SourceSongReviewPreviewRequest(
    val selection: SourceSongReviewPreview,
    val partId: String? = null,
    val boundaryId: String? = null
) {
    init {
        require((selection == SourceSongReviewPreview.SOURCE) == (partId != null) &&
            (selection == SourceSongReviewPreview.BOUNDARY) == (boundaryId != null)) {
            "Canonical melody preview selection is incomplete."
        }
    }
}

enum class PreviewStage { VALIDATE, DECODE_OR_RENDER, VALIDATE_ARTIFACT, REUSE_OR_PUBLISH }

sealed interface PreviewResult {
    data class Resolved(
        val artifact: Path,
        val stages: List<PreviewStage>,
        val reused: Boolean,
        /** Identity of the validated source representation, never a cache filename. */
        val source: PreviewArtifactIdentity? = null
    ) : PreviewResult
    data class Prerequisite(val stage: PreviewStage, val message: String) : PreviewResult
    data class Failed(val stage: PreviewStage, val message: String, val cause: Throwable? = null) : PreviewResult
}

data class PreviewArtifactIdentity(val label: String, val sha256: String) {
    init {
        require(label.isNotBlank() && '/' !in label && '\\' !in label) { "Preview artifact label is unsafe." }
        require(sha256.matches(Regex("[0-9a-f]{64}"))) { "Preview artifact fingerprint is invalid." }
    }
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

    /** Resolve one current source/prepared/full/boundary comparison monitor before arrangement approval. */
    suspend fun resolveSourceSongReview(projectRoot: Path, request: SourceSongReviewPreviewRequest): PreviewResult =
        PreviewResult.Failed(PreviewStage.VALIDATE, "Canonical melody review preview is unavailable from this preview service.")

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
    private val rendererConfigurationFingerprint: () -> String = { renderer.javaClass.name },
    private val sourceSongService: SourceSongApplicationService = SourceSongApplicationService(),
    private val melodyConnectionPlanner: MelodyConnectionPlanner = MelodyConnectionPlanner()
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

    /** Resolve source, prepared, full-melody, or two-occurrence boundary monitoring from the current canonical sidecars. */
    override suspend fun resolveSourceSongReview(projectRoot: Path, request: SourceSongReviewPreviewRequest): PreviewResult = withContext(Dispatchers.IO) {
        val stages = mutableListOf(PreviewStage.VALIDATE)
        try {
            val root = projectRoot.toAbsolutePath().normalize()
            val project = ProjectStore.read(root).also { it.requireValid(root) }
            val format = project.renderFormat
                ?: return@withContext PreviewResult.Prerequisite(PreviewStage.VALIDATE, "Project render format is required before previewing the canonical melody.")
            val sourceSong = sourceSongService.assemble(root).song
            val connection = melodyConnectionPlanner.connect(root, sourceSong).connection
            val selection = when (request.selection) {
                SourceSongReviewPreview.SOURCE -> {
                    val partId = requireNotNull(request.partId)
                    val part = project.parts.singleOrNull { it.id == partId }
                        ?: return@withContext PreviewResult.Prerequisite(PreviewStage.VALIDATE, "Source part '$partId' is no longer in the current project.")
                    val raw = part.midi?.raw
                        ?: return@withContext PreviewResult.Prerequisite(PreviewStage.VALIDATE, "Preserved source MIDI is unavailable for '$partId'.")
                    val rawPath = root.resolve(raw).normalize()
                    if (!rawPath.startsWith(root) || !Files.isRegularFile(rawPath) || Files.isSymbolicLink(rawPath)) {
                        return@withContext PreviewResult.Prerequisite(PreviewStage.VALIDATE, "Preserved source MIDI is unavailable for '$partId'.")
                    }
                    CanonicalReviewMidi("Preserved source $partId", WorkflowArtifactReference(raw, digest(Files.readAllBytes(rawPath))), "source-$partId")
                }
                SourceSongReviewPreview.PREPARED -> CanonicalReviewMidi("Prepared canonical melody", sourceSong.assembledMidi, "prepared")
                SourceSongReviewPreview.FULL_MELODY -> CanonicalReviewMidi("Connected full melody", connection.outputMidi, "full")
                SourceSongReviewPreview.BOUNDARY -> {
                    val boundaryId = requireNotNull(request.boundaryId)
                    val boundary = connection.boundaries.singleOrNull { it.decision.boundaryId == boundaryId }
                        ?: return@withContext PreviewResult.Prerequisite(PreviewStage.VALIDATE, "Canonical boundary '$boundaryId' is not in the current connected melody.")
                    val clipped = boundaryMidi(root, sourceSong, connection.outputMidi, boundary.decision.outgoingInstanceId, boundary.decision.incomingInstanceId, boundaryId)
                    CanonicalReviewMidi("Boundary $boundaryId", clipped, "boundary-$boundaryId")
                }
            }
            resolveCanonicalReviewMidi(root, format, selection, stages)
        } catch (error: IllegalArgumentException) {
            PreviewResult.Failed(PreviewStage.VALIDATE, error.message ?: "Canonical melody preview input is invalid.", error)
        } catch (error: Exception) {
            PreviewResult.Prerequisite(stages.lastOrNull() ?: PreviewStage.VALIDATE, "Piano preview renderer or sound library is unavailable: ${error.message ?: "unknown failure"}")
        }
    }

    /** Render a verified canonical MIDI reference through the same level-matched piano monitor. */
    private suspend fun resolveCanonicalReviewMidi(root: Path, format: RenderFormat, selection: CanonicalReviewMidi, stages: MutableList<PreviewStage>): PreviewResult {
        val midi = verified(root, selection.reference, selection.label)
        val duration = try { MidiSystem.getSequence(midi.toFile()).microsecondLength / 1_000_000.0 } catch (_: Exception) { 0.0 }
        if (!duration.isFinite() || duration <= 0.0) return PreviewResult.Prerequisite(PreviewStage.VALIDATE, "${selection.label} has no usable duration.")
        val frames = (duration * format.sampleRate).roundToLong()
        val target = previewTarget(root, "piano-review-${selection.id}", "canonical", fingerprint(midi, "${format.sampleRate}|${format.channels}|$frames|${rendererConfigurationFingerprint()}|rms-v1"))
        stages += PreviewStage.DECODE_OR_RENDER
        if (validMonitorWav(target, format.sampleRate, format.channels, 24, frames)) {
            return PreviewResult.Resolved(target, stages + PreviewStage.VALIDATE_ARTIFACT + PreviewStage.REUSE_OR_PUBLISH, true, identity(selection.label, midi))
        }
        val published = publish(target, { temporary ->
            renderer.render(midi, LogicalInstrument.PIANO, temporary, format, frames)
            loudnessMatchMonitor(temporary)
        }) { temporary -> validMonitorWav(temporary, format.sampleRate, format.channels, 24, frames) }
        return if (published) PreviewResult.Resolved(target, stages + PreviewStage.VALIDATE_ARTIFACT + PreviewStage.REUSE_OR_PUBLISH, false, identity(selection.label, midi))
        else PreviewResult.Failed(PreviewStage.VALIDATE_ARTIFACT, "Piano renderer did not produce a valid canonical-melody monitor artifact.")
    }

    /** Clip exactly the two canonical sidecar occurrences adjacent to one reviewed boundary without touching source candidates. */
    private fun boundaryMidi(
        root: Path,
        sourceSong: SourceSong,
        connected: WorkflowArtifactReference,
        outgoingId: String,
        incomingId: String,
        boundaryId: String
    ): WorkflowArtifactReference {
        val outgoing = requireNotNull(sourceSong.fullMelody.occurrences.singleOrNull { it.occurrenceId == outgoingId }) { "Boundary '$boundaryId' has no outgoing sidecar window." }
        val incoming = requireNotNull(sourceSong.fullMelody.occurrences.singleOrNull { it.occurrenceId == incomingId }) { "Boundary '$boundaryId' has no incoming sidecar window." }
        require(outgoing.endTick == incoming.startTick) { "Boundary '$boundaryId' is not contiguous in the canonical sidecar." }
        val source = verified(root, connected, "Connected full melody")
        val sequence = MidiSystem.getSequence(source.toFile())
        require(sequence.divisionType == Sequence.PPQ && sequence.resolution == sourceSong.canonicalPpq) { "Connected full melody has incompatible timing." }
        val duration = incoming.endTick - outgoing.startTick
        val fingerprint = digest(Files.readAllBytes(source) + "$boundaryId|${outgoing.startTick}|${incoming.endTick}".toByteArray())
        val target = root.resolve("previews/canonical-boundary-$boundaryId-$fingerprint.mid").normalize()
        require(target.startsWith(root)) { "Canonical boundary preview escapes the project root." }
        val clipped = Sequence(Sequence.PPQ, sequence.resolution)
        sequence.tracks.forEach { input ->
            val output = clipped.createTrack()
            (0 until input.size()).map(input::get)
                .filter { event -> event.tick in outgoing.startTick..incoming.endTick && (event.message as? MetaMessage)?.type != 0x2F }
                .forEach { event -> output.add(MidiEvent(event.message.clone() as MidiMessage, event.tick - outgoing.startTick)) }
            output.add(MidiEvent(MetaMessage(0x2F, byteArrayOf(), 0), duration))
        }
        Files.createDirectories(requireNotNull(target.parent))
        val temporary = target.resolveSibling(".${target.fileName}.${UUID.randomUUID()}.tmp")
        try {
            require(MidiSystem.write(clipped, 1, temporary.toFile()) > 0) { "Could not write canonical boundary preview MIDI." }
            val digest = digest(Files.readAllBytes(temporary))
            if (Files.exists(target)) {
                require(Files.isRegularFile(target) && !Files.isSymbolicLink(target) && digest(Files.readAllBytes(target)) == digest) {
                    "Existing canonical boundary preview differs; preserving it for inspection."
                }
            } else {
                try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE) }
                catch (_: java.nio.file.AtomicMoveNotSupportedException) { Files.move(temporary, target) }
            }
        } finally { Files.deleteIfExists(temporary) }
        return WorkflowArtifactReference(root.relativize(target).toString().replace('\\', '/'), digest(Files.readAllBytes(target)))
    }

    /** Verify a fingerprinted MIDI reference without exposing its local path to a caller. */
    private fun verified(root: Path, reference: WorkflowArtifactReference, label: String): Path {
        val path = root.resolve(reference.file).normalize()
        require(path.startsWith(root) && Files.isRegularFile(path) && !Files.isSymbolicLink(path) && digest(Files.readAllBytes(path)) == reference.sha256) {
            "$label is missing or stale. Regenerate the current canonical melody review."
        }
        return path
    }

    /** Match every canonical MIDI monitor to a fixed RMS target with a peak-safe ceiling for fair A/B listening. */
    private fun loudnessMatchMonitor(path: Path) {
        val audio = WAVDecoder(ErrorReporter.NoOp).decode(path)
        val rms = kotlin.math.sqrt(audio.samples.sumOf { sample -> sample.toDouble() * sample } / audio.samples.size.coerceAtLeast(1))
        if (rms <= 1e-9) return
        val requestedGain = (MONITOR_RMS / rms).coerceIn(0.25, 4.0)
        val peak = audio.samples.maxOfOrNull { sample -> abs(sample).toDouble() } ?: 0.0
        val gain = if (peak * requestedGain > MONITOR_PEAK) MONITOR_PEAK / peak else requestedGain
        val normalized = audio.copy(samples = FloatArray(audio.samples.size) { index -> (audio.samples[index] * gain).toFloat() })
        DeterministicStemMixer().writeWav(MixedStem(normalized, listOf("canonical-preview")), path)
    }

    private data class CanonicalReviewMidi(val label: String, val reference: WorkflowArtifactReference, val id: String)

    private fun resolveWavSource(source: Path, stages: MutableList<PreviewStage>): PreviewResult {
        val info = inspectWav(source) ?: return PreviewResult.Failed(PreviewStage.VALIDATE, "WAV source is not a supported RIFF PCM/float WAV.")
        if (info.frames <= 0) return PreviewResult.Failed(PreviewStage.VALIDATE, "WAV source contains no audio frames.")
        stages += PreviewStage.VALIDATE_ARTIFACT
        return PreviewResult.Resolved(source, stages + PreviewStage.REUSE_OR_PUBLISH, reused = true, source = identity("Original audio", source))
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
        return PreviewResult.Resolved(clean, stages + PreviewStage.REUSE_OR_PUBLISH, reused = true, source = identity("Prepared clean audio", clean))
    }

    private suspend fun resolveMp3(root: Path, partId: String, source: Path, stages: MutableList<PreviewStage>): PreviewResult {
        val target = previewTarget(root, "audio", partId, fingerprint(source, mp3Decoder.configurationFingerprint))
        stages += PreviewStage.DECODE_OR_RENDER
        if (validMonitorWav(target, null, null, null)) return PreviewResult.Resolved(target, stages + PreviewStage.VALIDATE_ARTIFACT + PreviewStage.REUSE_OR_PUBLISH, true, identity("Original audio", source))
        return try {
            val published = publish(target, { temporary -> mp3Decoder.decode(source, temporary) }) { temporary -> validMonitorWav(temporary, null, null, 24) }
            if (!published) {
                PreviewResult.Failed(PreviewStage.VALIDATE_ARTIFACT, "MP3 decoder did not produce a valid PCM-24 WAV monitor artifact.")
            } else PreviewResult.Resolved(target, stages + PreviewStage.VALIDATE_ARTIFACT + PreviewStage.REUSE_OR_PUBLISH, false, identity("Original audio", source))
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
        val target = previewTarget(root, "piano", partId, fingerprint(clean, "${format.sampleRate}|${format.channels}|$frames|${rendererConfigurationFingerprint()}|rms-v1"))
        stages += PreviewStage.DECODE_OR_RENDER
        if (validMonitorWav(target, format.sampleRate, format.channels, 24, frames)) return PreviewResult.Resolved(target, stages + PreviewStage.VALIDATE_ARTIFACT + PreviewStage.REUSE_OR_PUBLISH, true, identity("Current selected MIDI", clean))
        return try {
            val published = publish(target, { temporary ->
                renderer.render(clean, LogicalInstrument.PIANO, temporary, format, frames)
                loudnessMatchMonitor(temporary)
            }) { temporary -> validMonitorWav(temporary, format.sampleRate, format.channels, 24, frames) }
            if (!published) {
                PreviewResult.Failed(PreviewStage.VALIDATE_ARTIFACT, "Piano renderer did not produce a valid project-format WAV monitor artifact.")
            } else PreviewResult.Resolved(target, stages + PreviewStage.VALIDATE_ARTIFACT + PreviewStage.REUSE_OR_PUBLISH, false, identity("Current selected MIDI", clean))
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
            PreviewMidiSource.CORRECTED -> midi.technicalCorrection?.output?.file
            PreviewMidiSource.ENHANCED -> midi.enhancement?.output?.file
            PreviewMidiSource.AI_FIX_DRAFT -> midi.aiFix?.draft?.file
            PreviewMidiSource.AI_FIX_APPROVED -> midi.aiFix?.approved?.file
            PreviewMidiSource.LOFI_FEEL -> midi.feel?.derived?.file
        } ?: return PreviewResult.Prerequisite(PreviewStage.VALIDATE, "${source.name.lowercase().replaceFirstChar(Char::uppercase)} MIDI is not available for '${part.id}'.")
        if (source == PreviewMidiSource.CLEANED && midi.raw != null) {
            val current = midi.cleanup != null && midi.quality != null && MidiQualityReportStore.isCurrent(root, part.id, midi.raw, reference, midi.cleanup, midi.quality)
            if (!current) return PreviewResult.Prerequisite(PreviewStage.VALIDATE, "Cleaned MIDI quality evidence is missing or stale. Run Clean MIDI again.")
        }
        if (source == PreviewMidiSource.LOFI_FEEL) {
            val feel = midi.feel
            val base = runCatching {
                SelectedMidiArtifactResolver().resolveBeforeFeel(root, project, part)
            }.getOrElse { error ->
                return PreviewResult.Prerequisite(PreviewStage.VALIDATE, error.message ?: "Current MIDI base is unavailable for '${part.id}'.")
            }
            val input = app.melotrail.arrangement.WorkflowArtifactReference(base.projectRelativePath, base.sha256)
            if (feel == null || !MidiFeelReportStore.isCurrent(root, part.id, input, feel)) {
                return PreviewResult.Prerequisite(PreviewStage.VALIDATE, "Lo-fi Feel MIDI is unavailable or stale. Choose Original feel or regenerate the fixed profile.")
            }
        }
        if (source == PreviewMidiSource.CORRECTED) {
            val correction = midi.technicalCorrection
                ?: return PreviewResult.Prerequisite(PreviewStage.VALIDATE, "Corrected MIDI is not available for '${part.id}'.")
            if (runCatching {
                    correction.requireCanonical(part.id)
                    digest(Files.readAllBytes(root.resolve(correction.input.file))) == correction.input.sha256 &&
                        digest(Files.readAllBytes(root.resolve(correction.output.file))) == correction.output.sha256
                }.getOrDefault(false).not()) {
                return PreviewResult.Prerequisite(PreviewStage.VALIDATE, "Corrected MIDI is unavailable or stale. Recreate correction before previewing it.")
            }
        }
        if (source == PreviewMidiSource.ENHANCED) {
            val enhancement = midi.enhancement
                ?: return PreviewResult.Prerequisite(PreviewStage.VALIDATE, "Enhanced MIDI is not available for '${part.id}'.")
            if (runCatching {
                    enhancement.requireCanonical(part.id)
                    enhancement.approval == app.melotrail.arrangement.EnhancementApproval.APPROVED &&
                        digest(Files.readAllBytes(root.resolve(enhancement.input.file))) == enhancement.input.sha256 &&
                        digest(Files.readAllBytes(root.resolve(enhancement.output.file))) == enhancement.output.sha256
                }.getOrDefault(false).not()) {
                return PreviewResult.Prerequisite(PreviewStage.VALIDATE, "Enhanced MIDI is unavailable, unapproved, or stale. Select Corrected or generate a current enhancement draft.")
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
        val target = previewTarget(root, "piano-${source.name.lowercase()}", part.id, fingerprint(midiPath, "${format.sampleRate}|${format.channels}|$frames|${rendererConfigurationFingerprint()}|rms-v1"))
        stages += PreviewStage.DECODE_OR_RENDER
        if (validMonitorWav(target, format.sampleRate, format.channels, 24, frames)) return PreviewResult.Resolved(target, stages + PreviewStage.VALIDATE_ARTIFACT + PreviewStage.REUSE_OR_PUBLISH, true, identity(source.label(), midiPath))
        return try {
            val published = publish(target, { temporary ->
                renderer.render(midiPath, LogicalInstrument.PIANO, temporary, format, frames)
                loudnessMatchMonitor(temporary)
            }) { temporary -> validMonitorWav(temporary, format.sampleRate, format.channels, 24, frames) }
            if (!published) PreviewResult.Failed(PreviewStage.VALIDATE_ARTIFACT, "Piano renderer did not produce a valid project-format WAV monitor artifact.")
            else PreviewResult.Resolved(target, stages + PreviewStage.VALIDATE_ARTIFACT + PreviewStage.REUSE_OR_PUBLISH, false, identity(source.label(), midiPath))
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
    private fun identity(label: String, source: Path) = PreviewArtifactIdentity(label, digest(Files.readAllBytes(source)))
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

    private companion object {
        val json = Json { ignoreUnknownKeys = false }
        val PART_ID = Regex("[A-Za-z0-9_-]+")
        const val MONITOR_RMS = 0.1
        const val MONITOR_PEAK = 0.98
    }
}

private fun PreviewMidiSource.label(): String = when (this) {
    PreviewMidiSource.RAW -> "Original MIDI"
    PreviewMidiSource.CLEANED -> "Cleaned MIDI"
    PreviewMidiSource.CORRECTED -> "Corrected MIDI"
    PreviewMidiSource.ENHANCED -> "Enhanced MIDI"
    PreviewMidiSource.AI_FIX_DRAFT -> "AI-fix draft MIDI"
    PreviewMidiSource.AI_FIX_APPROVED -> "Approved AI-fix MIDI"
    PreviewMidiSource.LOFI_FEEL -> "Lo-fi Feel MIDI"
}

private fun java.io.RandomAccessFile.readFourCc(): String = ByteArray(4).also { readFully(it) }.toString(Charsets.US_ASCII)
private fun java.io.RandomAccessFile.readUInt16(): Int = read() or (read() shl 8)
private fun java.io.RandomAccessFile.readUInt32(): Long = readUInt16().toLong() or (readUInt16().toLong() shl 16)
