package app.melotrail.preparation

import app.melotrail.arrangement.MidiPartAnalyzer
import app.melotrail.worker.TranscribeCommand
import app.melotrail.worker.WorkerClient
import app.melotrail.worker.WorkerStatus
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import javax.sound.midi.MidiSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap

private const val PIANO_MIN_PITCH = 21
private const val PIANO_MAX_PITCH = 108

/** Structured failure stages that adapters can display and log without parsing worker text. */
@Serializable
enum class TranscriptionFailureStage {
    PREREQUISITE,
    DECODE,
    CLEANUP_SELECTION,
    MODEL_RUNTIME,
    INFERENCE,
    OUTPUT_VALIDATION
}

@Serializable enum class TranscriptionGateStatus { SUCCEEDED, FAILED }

@Serializable
data class TranscriptionGateMetrics(
    val noteCount: Int,
    val durationSeconds: Double,
    val notesPerSecond: Double,
    val minPitch: Int,
    val maxPitch: Int
) {
    fun requireValid() {
        require(noteCount > 0) { "Transcription note count must be positive." }
        require(durationSeconds.isFinite() && durationSeconds > 0.0) { "Transcription duration must be finite and positive." }
        require(notesPerSecond.isFinite() && notesPerSecond >= 0.0) { "Transcription note rate must be finite." }
        require(minPitch in PIANO_MIN_PITCH..PIANO_MAX_PITCH && maxPitch in minPitch..PIANO_MAX_PITCH) {
            "Transcription pitch range is outside the piano range."
        }
    }
}

/** Persisted, path-safe gate metadata appended to the preparation report. */
@Serializable
data class TranscriptionGateRecord(
    val status: TranscriptionGateStatus,
    val selectedInput: TranscriptionInputArtifact? = null,
    val selectedInputFingerprint: String? = null,
    val engine: String? = null,
    val engineVersion: String? = null,
    val metrics: TranscriptionGateMetrics? = null,
    val failureStage: TranscriptionFailureStage? = null,
    val warnings: List<String> = emptyList(),
    /** Project-relative retained invalid-but-parseable worker output, if any. */
    val diagnosticRawMidi: String? = null
) {
    fun requireValid(partId: String) {
        selectedInputFingerprint?.let { require(SHA_256.matches(it)) { "Transcription input fingerprint is invalid." } }
        engine?.let { require(SAFE_TOKEN.matches(it)) { "Transcription engine is invalid." } }
        engineVersion?.let { requireSafeGateText(it, "Transcription engine version", 120) }
        warnings.forEach { requireSafeGateText(it, "Transcription warning", 500) }
        diagnosticRawMidi?.let {
            require(DIAGNOSTIC_MIDI.matches(it) && it.startsWith("midi/diagnostics/$partId-")) {
                "Transcription diagnostic MIDI path is invalid."
            }
        }
        when (status) {
            TranscriptionGateStatus.SUCCEEDED -> {
                require(selectedInput != null && selectedInputFingerprint != null && engine != null && engineVersion != null && metrics != null) {
                    "Successful transcription records require input, engine, and metrics."
                }
                require(failureStage == null && diagnosticRawMidi == null) { "Successful transcription cannot have failure details." }
                metrics.requireValid()
            }
            TranscriptionGateStatus.FAILED -> require(failureStage != null) { "Failed transcription records require a failure stage." }
        }
    }
}

/** The request contains only an enum identifier; no caller-controlled input/output path is accepted. */
data class RunTranscriptionQualityGateRequest(
    val projectRoot: Path,
    val partId: String,
    val selectedInput: TranscriptionInputArtifact
)

@Serializable
data class TranscriptionEngineMetadata(val engine: String, val version: String) {
    fun requireValid() {
        require(SAFE_TOKEN.matches(engine)) { "Transcription engine is invalid." }
        requireSafeGateText(version, "Transcription engine version", 120)
    }
}

sealed interface TranscriptionBoundaryResult {
    data class Completed(val metadata: TranscriptionEngineMetadata) : TranscriptionBoundaryResult
    data class Failed(val stage: TranscriptionFailureStage) : TranscriptionBoundaryResult
}

/** Small fakeable boundary. The quality gate owns every project path and publication decision. */
fun interface TranscriptionBoundary {
    suspend fun transcribe(input: Path, output: Path): TranscriptionBoundaryResult
}

/** Strict worker adapter: worker text and paths never enter persisted reports. */
class WorkerTranscriptionBoundary(private val workerClient: WorkerClient) : TranscriptionBoundary {
    override suspend fun transcribe(input: Path, output: Path): TranscriptionBoundaryResult {
        val response = workerClient.execute(TranscribeCommand(input.toString(), output.toString(), "piano"))
        if (response.status != WorkerStatus.COMPLETED) {
            return TranscriptionBoundaryResult.Failed(workerStage(response.error?.type, response.error?.message))
        }
        return try {
            val responseOutput = response.output?.let(::JsonObject) ?: return TranscriptionBoundaryResult.Failed(TranscriptionFailureStage.OUTPUT_VALIDATION)
            val engine = responseOutput["engine"]?.jsonPrimitive?.content
                ?: return TranscriptionBoundaryResult.Failed(TranscriptionFailureStage.OUTPUT_VALIDATION)
            val version = responseOutput["engineVersion"]?.jsonPrimitive?.content
                ?: return TranscriptionBoundaryResult.Failed(TranscriptionFailureStage.OUTPUT_VALIDATION)
            TranscriptionBoundaryResult.Completed(TranscriptionEngineMetadata(engine, version).also { it.requireValid() })
        } catch (_: Exception) {
            TranscriptionBoundaryResult.Failed(TranscriptionFailureStage.OUTPUT_VALIDATION)
        }
    }

    private fun workerStage(type: String?, message: String?): TranscriptionFailureStage = when (type) {
        "TranscriptionDecodeError" -> TranscriptionFailureStage.DECODE
        "TranscriptionOutputValidationError" -> TranscriptionFailureStage.OUTPUT_VALIDATION
        "TranscriptionValidationError" -> TranscriptionFailureStage.PREREQUISITE
        "TranscriptionModelError" -> if (message?.contains("unavailable", ignoreCase = true) == true) {
            TranscriptionFailureStage.MODEL_RUNTIME
        } else TranscriptionFailureStage.INFERENCE
        else -> TranscriptionFailureStage.INFERENCE
    }
}

sealed interface TranscriptionQualityGateResult {
    data class Succeeded(val report: InputInspectionReport, val rawMidi: Path, val metrics: TranscriptionGateMetrics) : TranscriptionQualityGateResult
    data class Failed(val report: InputInspectionReport?, val stage: TranscriptionFailureStage) : TranscriptionQualityGateResult
}

/**
 * Validates a worker-produced MIDI file before atomically making it canonical
 * `midi/raw/<part>.mid`. It deliberately does not clean, analyze, register a
 * part, or run an audio worker itself.
 */
class TranscriptionQualityGateService(private val boundary: TranscriptionBoundary) {
    suspend fun run(request: RunTranscriptionQualityGateRequest): TranscriptionQualityGateResult = withContext(Dispatchers.IO) {
        InputInspectionPaths.requirePartId(request.partId)
        val root = request.projectRoot.toAbsolutePath().normalize()
        val lock = locks.computeIfAbsent(root) { Mutex() }
        lock.withLock { runLocked(root, request) }
    }

    private suspend fun runLocked(root: Path, request: RunTranscriptionQualityGateRequest): TranscriptionQualityGateResult {
        val report = runCatching { InputInspectionReportStore.read(root, request.partId) }.getOrElse {
            return TranscriptionQualityGateResult.Failed(null, TranscriptionFailureStage.PREREQUISITE)
        }
        val source = projectSource(root, report.source)
            ?: return persistFailure(root, report, TranscriptionFailureStage.PREREQUISITE, request.selectedInput)
        if (sha256(source) != report.source.sha256) return TranscriptionQualityGateResult.Failed(report, TranscriptionFailureStage.PREREQUISITE)

        val selected = resolveSelectedInput(root, report, request.selectedInput)
            ?: return persistFailure(root, report, selectionFailure(request.selectedInput), request.selectedInput)
        val inputDuration = selected.durationSeconds
        val raw = InputInspectionPaths.rawMidi(root, request.partId)
        val temporary = raw.resolveSibling(".${raw.fileName}.gate-${UUID.randomUUID()}.mid")
        try {
            Files.createDirectories(raw.parent)
            require(raw.parent.toRealPath().startsWith(root.toRealPath())) { "Raw MIDI destination escapes the project." }
            when (val result = boundary.transcribe(selected.path, temporary)) {
                is TranscriptionBoundaryResult.Failed -> return persistFailure(root, report, result.stage, request.selectedInput, selected)
                is TranscriptionBoundaryResult.Completed -> {
                    result.metadata.requireValid()
                    val metrics = gateMetrics(temporary, inputDuration)
                    atomicReplace(temporary, raw, "transcription raw MIDI")
                    val updated = report.copy(transcription = TranscriptionGateRecord(
                        status = TranscriptionGateStatus.SUCCEEDED,
                        selectedInput = request.selectedInput,
                        selectedInputFingerprint = selected.fingerprint,
                        engine = result.metadata.engine,
                        engineVersion = result.metadata.version,
                        metrics = metrics,
                        warnings = durationWarning(metrics.durationSeconds, inputDuration)
                    ))
                    InputInspectionReportStore.write(root, updated)
                    return TranscriptionQualityGateResult.Succeeded(updated, raw, metrics)
                }
            }
        } catch (_: Exception) {
            val diagnostic = retainDiagnosticMidi(root, request.partId, temporary)
            return persistFailure(root, report, TranscriptionFailureStage.OUTPUT_VALIDATION, request.selectedInput, selected, diagnostic)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun resolveSelectedInput(root: Path, report: InputInspectionReport, selection: TranscriptionInputArtifact): SelectedInput? = when (selection) {
        TranscriptionInputArtifact.SOURCE -> projectSource(root, report.source)
            ?.takeIf { report.detectedInput.container != InputContainer.MIDI }
            ?.let { SelectedInput(it, sha256(it), report.durationSeconds) }
        TranscriptionInputArtifact.DECODED_WAV -> projectArtifact(root, InputInspectionPaths.decodedWav(root, report.partId))?.let(::selectedInput)
        TranscriptionInputArtifact.CLEAN_WAV -> {
            val cleanup = report.cleanup
            val output = cleanup?.output
            if (cleanup == null || output == null || cleanup.plan.transcriptionInput != TranscriptionInputArtifact.CLEAN_WAV) null
            else projectArtifact(root, InputInspectionPaths.cleanWav(root, report.partId))?.let { path ->
                selectedInput(path)?.takeIf { it.fingerprint == output.sha256 }
            }
        }
    }

    private fun selectedInput(path: Path): SelectedInput? {
        val duration = audioDuration(path) ?: return null
        return SelectedInput(path, sha256(path), duration)
    }

    private fun gateMetrics(path: Path, inputDuration: Double): TranscriptionGateMetrics {
        require(Files.isRegularFile(path) && Files.size(path) >= 14) { "Transcription output is not a MIDI file." }
        val analysis = MidiPartAnalyzer().analyze(path, "gate")
        val range = requireNotNull(analysis.pitchRange) { "Transcription output contains no notes." }
        require(analysis.noteCount > 0) { "Transcription output contains no notes." }
        require(range.min >= PIANO_MIN_PITCH && range.max <= PIANO_MAX_PITCH) { "Transcription output is outside the piano pitch range." }
        require(analysis.durationSeconds.isFinite() && analysis.durationSeconds > 0.0) { "Transcription output has invalid timing." }
        val noteRate = analysis.noteCount / analysis.durationSeconds
        require(noteRate.isFinite() && noteRate <= MAX_NOTES_PER_SECOND) { "Transcription output note rate is too dense." }
        require(kotlin.math.abs(analysis.durationSeconds - inputDuration) <= maxOf(MAX_DURATION_DELTA_SECONDS, inputDuration * MAX_DURATION_DELTA_RATIO)) {
            "Transcription output duration does not match its selected audio input."
        }
        return TranscriptionGateMetrics(analysis.noteCount, analysis.durationSeconds, noteRate, range.min, range.max).also { it.requireValid() }
    }

    private fun persistFailure(
        root: Path,
        report: InputInspectionReport,
        stage: TranscriptionFailureStage,
        selection: TranscriptionInputArtifact,
        selected: SelectedInput? = null,
        diagnostic: String? = null
    ): TranscriptionQualityGateResult.Failed {
        val updated = report.copy(transcription = TranscriptionGateRecord(
            status = TranscriptionGateStatus.FAILED,
            selectedInput = selection,
            selectedInputFingerprint = selected?.fingerprint,
            failureStage = stage,
            warnings = listOf(failureWarning(stage)),
            diagnosticRawMidi = diagnostic
        ))
        InputInspectionReportStore.write(root, updated)
        return TranscriptionQualityGateResult.Failed(updated, stage)
    }

    private fun selectionFailure(selection: TranscriptionInputArtifact) = when (selection) {
        TranscriptionInputArtifact.CLEAN_WAV -> TranscriptionFailureStage.CLEANUP_SELECTION
        else -> TranscriptionFailureStage.DECODE
    }

    private fun projectSource(root: Path, source: InspectionSourceIdentity): Path? = runCatching {
        val path = root.resolve(source.relativePath).normalize()
        require(path.startsWith(root) && Files.isRegularFile(path) && path.toRealPath().startsWith(root.toRealPath()))
        path
    }.getOrNull()

    private fun projectArtifact(root: Path, path: Path): Path? = runCatching {
        require(path.startsWith(root) && Files.isRegularFile(path) && path.toRealPath().startsWith(root.toRealPath()))
        path
    }.getOrNull()

    private fun retainDiagnosticMidi(root: Path, partId: String, temporary: Path): String? = runCatching {
        if (!Files.isRegularFile(temporary) || Files.size(temporary) < 14) return@runCatching null
        MidiSystem.getSequence(temporary.toFile())
        val directory = InputInspectionPaths.diagnosticMidiDirectory(root)
        Files.createDirectories(directory)
        require(directory.toRealPath().startsWith(root.toRealPath()))
        val name = "$partId-${UUID.randomUUID()}.mid"
        val target = directory.resolve(name).normalize()
        require(target.startsWith(directory) && target.parent == directory)
        atomicReplace(temporary, target, "transcription diagnostic MIDI")
        "midi/diagnostics/$name"
    }.getOrNull()

    private fun audioDuration(path: Path): Double? = runCatching {
        val bytes = Files.readAllBytes(path)
        require(bytes.size >= 44 && bytes.copyOfRange(0, 4).decodeToString() == "RIFF" && bytes.copyOfRange(8, 12).decodeToString() == "WAVE")
        var position = 12; var sampleRate = 0; var blockAlign = 0; var dataSize = -1
        while (position + 8 <= bytes.size) {
            val id = bytes.copyOfRange(position, position + 4).decodeToString()
            val size = littleEndianInt(bytes, position + 4)
            position += 8
            require(size >= 0 && position + size <= bytes.size)
            if (id == "fmt ") {
                require(size >= 16)
                sampleRate = littleEndianInt(bytes, position + 4)
                blockAlign = littleEndianShort(bytes, position + 12)
            } else if (id == "data") dataSize = size
            position += size + (size and 1)
        }
        require(sampleRate in 1..384_000 && blockAlign > 0 && dataSize >= 0 && dataSize % blockAlign == 0)
        (dataSize.toDouble() / blockAlign) / sampleRate
    }.getOrNull()?.takeIf { it.isFinite() && it > 0.0 }

    private fun atomicReplace(source: Path, target: Path, label: String) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (error: AtomicMoveNotSupportedException) {
            throw IllegalStateException("Atomic publish is not supported for $label.", error)
        }
    }

    private companion object {
        const val MAX_NOTES_PER_SECOND = 40.0
        const val MAX_DURATION_DELTA_SECONDS = 2.0
        const val MAX_DURATION_DELTA_RATIO = 0.25
        val locks = ConcurrentHashMap<Path, Mutex>()
    }

    private data class SelectedInput(val path: Path, val fingerprint: String, val durationSeconds: Double)
}

private val SHA_256 = Regex("[0-9a-f]{64}")
private val SAFE_TOKEN = Regex("[A-Za-z0-9._+-]{1,64}")
private val DIAGNOSTIC_MIDI = Regex("midi/diagnostics/[A-Za-z0-9_-]{1,64}-[0-9a-f-]{36}\\.mid")

private fun failureWarning(stage: TranscriptionFailureStage) = when (stage) {
    TranscriptionFailureStage.PREREQUISITE -> "Transcription prerequisites are not satisfied."
    TranscriptionFailureStage.DECODE -> "Selected audio could not be decoded."
    TranscriptionFailureStage.CLEANUP_SELECTION -> "Selected prepared audio is unavailable or stale."
    TranscriptionFailureStage.MODEL_RUNTIME -> "The local transcription runtime is unavailable."
    TranscriptionFailureStage.INFERENCE -> "The local transcription engine did not complete inference."
    TranscriptionFailureStage.OUTPUT_VALIDATION -> "Transcription output did not pass MIDI quality validation."
}

private fun durationWarning(duration: Double, inputDuration: Double): List<String> =
    if (kotlin.math.abs(duration - inputDuration) > inputDuration * 0.1) listOf("Transcription duration differs from selected audio by more than ten percent.") else emptyList()

private fun sha256(path: Path): String {
    val digest = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(path).use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

private fun littleEndianShort(bytes: ByteArray, offset: Int): Int =
    (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

private fun littleEndianInt(bytes: ByteArray, offset: Int): Int =
    (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8) or
        ((bytes[offset + 2].toInt() and 0xff) shl 16) or ((bytes[offset + 3].toInt() and 0xff) shl 24)

private fun requireSafeGateText(value: String, label: String, maxLength: Int) {
    require(value.isNotBlank() && value.length <= maxLength && value.none { it.isISOControl() }) { "$label is invalid." }
    require(!Regex("(?:^|\\s)(?:[A-Za-z]:[\\\\/]|/|~[/\\\\])").containsMatchIn(value)) { "$label must not contain an external path." }
}
