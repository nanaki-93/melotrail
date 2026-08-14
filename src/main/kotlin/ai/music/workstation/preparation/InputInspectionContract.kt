package ai.music.workstation.preparation

import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Stable boundary for inspecting one project-local MIDI, WAV, or MP3 source.
 *
 * This contract intentionally contains identities and measured values only.  A
 * later service resolves the source inside the project and publishes the
 * report; inspection itself never registers a part, copies a source, or makes
 * a prepared audio file.
 */
data class InputInspectionRequest(
    val projectRoot: Path,
    val partId: String,
    val source: InspectionSourceIdentity
) {
    fun requireValid() {
        InputInspectionPaths.requirePartId(partId)
        source.requireValid()
    }
}

/** A persisted source identity; [relativePath] is always below `source/`. */
@Serializable
data class InspectionSourceIdentity(
    val relativePath: String,
    val sha256: String
) {
    fun requireValid() {
        requireProjectRelativeSource(relativePath)
        require(SHA_256.matches(sha256)) { "Source fingerprint must be a lowercase SHA-256 digest." }
    }
}

/** Inert seam for Task 037's worker-backed measurement implementation. */
interface InputInspectionBoundary {
    suspend fun inspect(request: InputInspectionRequest): InputInspectionResult
}

sealed interface InputInspectionResult {
    data class Inspected(val report: InputInspectionReport) : InputInspectionResult
    data class Rejected(val error: InputInspectionError) : InputInspectionResult
}

@Serializable
data class InputInspectionError(
    val code: InputInspectionErrorCode,
    val message: String
) {
    fun requireValid() {
        requireSafeText(message, "Inspection error message", 500)
    }
}

@Serializable
enum class InputInspectionErrorCode {
    INVALID_REQUEST,
    UNSUPPORTED_INPUT,
    INVALID_CONTAINER,
    DECODING_FAILED,
    MEASUREMENT_FAILED,
    INVALID_MEASUREMENT,
    SOURCE_CHANGED
}

@Serializable
data class InputInspectionReport(
    val version: Int = CURRENT_VERSION,
    val partId: String,
    val source: InspectionSourceIdentity,
    val detectedInput: DetectedInput,
    val durationSeconds: Double,
    val audioFormat: DetectedAudioFormat? = null,
    val measurements: AudioInspectionMeasurements? = null,
    val warnings: List<String> = emptyList(),
    val toolVersions: Map<String, String> = emptyMap(),
    val preparation: PreparationStatus = PreparationStatus.INSPECT_ONLY
) {
    fun requireValid() {
        require(version == CURRENT_VERSION) { "Unsupported input inspection report version: $version" }
        InputInspectionPaths.requirePartId(partId)
        source.requireValid()
        detectedInput.requireValid()
        requireFinite(durationSeconds, "Duration")
        require(durationSeconds > 0.0) { "Duration must be greater than zero." }
        if (detectedInput.container == InputContainer.MIDI) {
            require(audioFormat == null && measurements == null) { "MIDI reports cannot contain audio measurements." }
        } else {
            requireNotNull(audioFormat) { "Audio reports require a detected audio format." }.requireValid()
            requireNotNull(measurements) { "Audio reports require measurements." }.requireValid()
        }
        require(warnings.size <= MAX_WARNINGS) { "Too many inspection warnings." }
        warnings.forEach { requireSafeText(it, "Inspection warning", 500) }
        require(toolVersions.size <= MAX_TOOL_VERSIONS) { "Too many tool versions." }
        toolVersions.forEach { (name, version) ->
            require(TOOL_NAME.matches(name)) { "Tool version name is invalid." }
            requireSafeText(version, "Tool version", 120)
        }
    }

    companion object {
        const val CURRENT_VERSION = 1
        private const val MAX_WARNINGS = 32
        private const val MAX_TOOL_VERSIONS = 16
        private val TOOL_NAME = Regex("[A-Za-z0-9._-]{1,64}")
    }
}

@Serializable
data class DetectedInput(
    val container: InputContainer,
    val codec: String,
    val extension: String
) {
    fun requireValid() {
        require(CODEC.matches(codec)) { "Detected codec is invalid." }
        require(extension == extension.lowercase() && EXTENSION.matches(extension)) { "Detected extension is invalid." }
        require(extension in container.extensions) { "Extension '$extension' does not match ${container.name} container." }
    }

    companion object {
        private val CODEC = Regex("[A-Za-z0-9._+-]{1,64}")
        private val EXTENSION = Regex("[a-z0-9]{1,8}")
    }
}

@Serializable
enum class InputContainer(val extensions: Set<String>) {
    MIDI(setOf("mid", "midi")),
    RIFF_WAVE(setOf("wav", "wave")),
    MPEG_AUDIO(setOf("mp3"))
}

@Serializable
data class DetectedAudioFormat(
    val sampleRate: Int,
    val channels: Int,
    val bitsPerSample: Int? = null
) {
    fun requireValid() {
        require(sampleRate in 1..384_000) { "Sample rate is outside the supported inspection range." }
        require(channels in 1..32) { "Channel count is outside the supported inspection range." }
        require(bitsPerSample == null || bitsPerSample in setOf(8, 16, 24, 32)) { "Bits per sample is invalid." }
    }
}

@Serializable
data class AudioInspectionMeasurements(
    val peak: Double,
    val rms: Double,
    val dcOffset: Double,
    val clippedRunCount: Long,
    val clippedFrameCount: Long,
    val silence: SilenceEvidence,
    val hum: SignalIndicator,
    val noise: SignalIndicator
) {
    fun requireValid() {
        requireFinite(peak, "Peak"); require(peak >= 0.0) { "Peak cannot be negative." }
        requireFinite(rms, "RMS"); require(rms >= 0.0) { "RMS cannot be negative." }
        requireFinite(dcOffset, "DC offset")
        require(clippedRunCount >= 0) { "Clipped run count cannot be negative." }
        require(clippedFrameCount >= 0) { "Clipped frame count cannot be negative." }
        silence.requireValid(); hum.requireValid(); noise.requireValid()
    }
}

@Serializable
data class SilenceEvidence(val silentFrames: Long, val longestSilentFrames: Long) {
    fun requireValid() {
        require(silentFrames >= 0 && longestSilentFrames >= 0) { "Silence frame counts cannot be negative." }
        require(longestSilentFrames <= silentFrames) { "Longest silence cannot exceed total silence." }
    }
}

@Serializable
data class SignalIndicator(val evidence: EvidenceLevel, val confidence: Double) {
    fun requireValid() {
        requireFinite(confidence, "Signal-indicator confidence")
        require(confidence in 0.0..1.0) { "Signal-indicator confidence must be between zero and one." }
    }
}

@Serializable
enum class EvidenceLevel { NONE, LOW, MODERATE, HIGH }

@Serializable
enum class PreparationStatus { INSPECT_ONLY, CLEANUP_PLANNED, CLEANED, TRANSCRIPTION_SELECTED }

/** Exact canonical locations for Task 036 through Task 043 preparation artifacts. */
object InputInspectionPaths {
    private val PART_ID = Regex("[A-Za-z0-9_-]{1,64}")

    fun preparedDirectory(projectRoot: Path, partId: String): Path = resolvedProjectPath(projectRoot, "prepared/$partId")
    fun report(projectRoot: Path, partId: String): Path = preparedDirectory(projectRoot, partId).resolve("report.json")
    fun decodedWav(projectRoot: Path, partId: String): Path = preparedDirectory(projectRoot, partId).resolve("decoded.wav")
    fun cleanWav(projectRoot: Path, partId: String): Path = preparedDirectory(projectRoot, partId).resolve("clean.wav")

    fun requirePartId(partId: String) {
        require(PART_ID.matches(partId)) { "Part ID is invalid." }
    }

    private fun resolvedProjectPath(projectRoot: Path, relative: String): Path {
        val root = projectRoot.toAbsolutePath().normalize()
        val path = root.resolve(relative).normalize()
        require(path.startsWith(root)) { "Preparation artifact must remain inside the project." }
        return path
    }
}

/** Strict, version-aware `report.json` serializer and atomic publisher. */
object InputInspectionReportStore {
    private val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = false }

    fun read(projectRoot: Path, partId: String): InputInspectionReport {
        val target = InputInspectionPaths.report(projectRoot, partId)
        val text = Files.readString(target, StandardCharsets.UTF_8)
        val objectValue = json.parseToJsonElement(text).jsonObject
        val version = objectValue["version"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1
        require(version == InputInspectionReport.CURRENT_VERSION) { "Unsupported input inspection report version: $version" }
        val report = json.decodeFromString<InputInspectionReport>(text)
        require(report.partId == partId) { "Inspection report part ID does not match its project path." }
        report.requireValid()
        return report
    }

    fun write(projectRoot: Path, report: InputInspectionReport) {
        report.requireValid()
        val target = InputInspectionPaths.report(projectRoot, report.partId)
        Files.createDirectories(target.parent)
        val temporary = target.resolveSibling(".${target.fileName}.${UUID.randomUUID()}.tmp")
        try {
            Files.writeString(temporary, json.encodeToString(report), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}

private val SHA_256 = Regex("[0-9a-f]{64}")

private fun requireProjectRelativeSource(value: String) {
    require(value.isNotBlank() && !value.startsWith('/') && !value.startsWith('\\')) { "Source identity must be project-relative." }
    val path = Path.of(value).normalize()
    require(!path.isAbsolute && path.nameCount >= 2 && path.getName(0).toString() == "source" && !value.contains('\\')) {
        "Source identity must remain below source/."
    }
}

private fun requireFinite(value: Double, name: String) {
    require(value.isFinite()) { "$name must be finite." }
}

private fun requireSafeText(value: String, name: String, maxLength: Int) {
    require(value.isNotBlank() && value.length <= maxLength && value.none { it.isISOControl() }) { "$name is invalid." }
    require(!ABSOLUTE_PATH.containsMatchIn(value)) { "$name must not contain an external path." }
}

private val ABSOLUTE_PATH = Regex("(?:^|\\s)(?:[A-Za-z]:[\\\\/]|/|~[/\\\\])")
