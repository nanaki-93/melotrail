package app.melotrail.preparation

import app.melotrail.worker.AnalyzeCommand
import app.melotrail.worker.AnalyzeOptions
import app.melotrail.worker.WorkerGateway
import app.melotrail.worker.WorkerStatus
import app.melotrail.arrangement.sha256
import java.nio.file.Files
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Project-confined request for one worker timing measurement. */
data class SourceTimingMeasurementRequest(
    val projectRoot: java.nio.file.Path,
    val partId: String,
    val source: InspectionSourceIdentity
) {
    /** Rejects invalid source identity before it can become a worker path. */
    fun requireValid() {
        SourceTimingPaths.requirePartId(partId)
        source.requireValid()
    }
}

/** Worker-independent boundary for source timing measurements. */
fun interface SourceTimingBoundary {
    /** Measures project-confined source timing or returns an allow-listed failure. */
    suspend fun measure(request: SourceTimingMeasurementRequest): SourceTimingMeasurementResult
}

/** A successful worker measurement or a safe, presentation-ready rejection. */
sealed interface SourceTimingMeasurementResult {
    data class Measured(val observation: SourceTimingObservation) : SourceTimingMeasurementResult
    data class Rejected(val error: SourceTimingError) : SourceTimingMeasurementResult
}

/** Allow-listed failure codes prevent raw worker errors and paths entering project evidence. */
@Serializable
enum class SourceTimingErrorCode { INVALID_REQUEST, DEPENDENCY_UNAVAILABLE, WORKER_REJECTED, INVALID_MEASUREMENT, SOURCE_CHANGED }

/** Safe timing-analysis recovery information. */
@Serializable
data class SourceTimingError(val code: SourceTimingErrorCode, val message: String) {
    /** Validates bounded, path-free recovery text. */
    fun requireValid() {
        require(message.isNotBlank() && message.length <= 240 && message.none { it.isISOControl() } && '/' !in message && '\\' !in message) {
            "Source timing error message is invalid"
        }
    }
}

/** Validated timing facts before Kotlin binds them to a source and derives a groove template. */
data class SourceTimingObservation(
    val workerContractVersion: Int,
    val beats: List<SourceTimingPoint>,
    val onsets: List<SourceTimingPoint>,
    val tempoCandidates: List<TempoCandidate>,
    val leadingActivity: SourceTimingActivity?,
    val downbeat: DownbeatEvidence
) {
    /** Converts worker facts into immutable, source-bound Kotlin evidence. */
    fun toEvidence(partId: String, source: InspectionSourceIdentity): SourceTimingEvidence = SourceTimingEvidence(
        partId = partId,
        source = source,
        workerContractVersion = workerContractVersion,
        beats = beats,
        onsets = onsets,
        tempoCandidates = tempoCandidates,
        leadingActivity = leadingActivity,
        downbeat = downbeat,
        groove = SourceGrooveTemplateDeriver.derive(source.sha256, beats, onsets)
    ).also(SourceTimingEvidence::requireValid)
}

/** Maps the pinned worker-v2 payload into Kotlin's source-safe evidence boundary. */
class WorkerSourceTimingBoundary(private val worker: WorkerGateway) : SourceTimingBoundary {
    private val json = Json { ignoreUnknownKeys = true }

    /** Resolves, fingerprints, and submits only a project-confined source to the compatible local worker. */
    override suspend fun measure(request: SourceTimingMeasurementRequest): SourceTimingMeasurementResult {
        request.requireValid()
        val root = request.projectRoot.toAbsolutePath().normalize()
        val source = root.resolve(request.source.relativePath).normalize()
        if (!source.startsWith(root) || !Files.isRegularFile(source) || runCatching { !source.toRealPath().startsWith(root.toRealPath()) }.getOrDefault(true)) {
            return rejected(SourceTimingErrorCode.INVALID_REQUEST, "Timing source is not a project-local file.")
        }
        if (sha256(source) != request.source.sha256) return rejected(SourceTimingErrorCode.SOURCE_CHANGED, "Timing source changed; inspect it again.")
        if (!worker.supportsTimingAnalysis(SourceTimingEvidence.WORKER_CONTRACT_VERSION)) {
            return rejected(SourceTimingErrorCode.DEPENDENCY_UNAVAILABLE, "Start a worker that supports timing analysis v2.")
        }
        val response = try {
            worker.execute(AnalyzeCommand(
                path = source.toString(),
                version = SourceTimingEvidence.WORKER_CONTRACT_VERSION,
                options = AnalyzeOptions(detectKey = false, detectLoudness = false, detectSections = false)
            ))
        } catch (_: Exception) {
            return rejected(SourceTimingErrorCode.DEPENDENCY_UNAVAILABLE, "Source timing worker is unavailable.")
        }
        if (sha256(source) != request.source.sha256) return rejected(SourceTimingErrorCode.SOURCE_CHANGED, "Timing source changed during analysis.")
        if (response.status != WorkerStatus.COMPLETED) return rejected(errorCode(response.error?.type), "Source timing worker did not complete.")
        return try {
            val output = response.output?.let(::JsonObject) ?: return rejected(SourceTimingErrorCode.INVALID_MEASUREMENT, "Source timing worker returned no measurements.")
            val payload = json.decodeFromJsonElement(WorkerTimingPayload.serializer(), output)
            SourceTimingMeasurementResult.Measured(payload.toObservation())
        } catch (_: Exception) {
            rejected(SourceTimingErrorCode.INVALID_MEASUREMENT, "Source timing worker returned invalid measurements.")
        }
    }

    /** Creates an error result only after validating that it is safe to present and persist. */
    private fun rejected(code: SourceTimingErrorCode, message: String): SourceTimingMeasurementResult.Rejected =
        SourceTimingMeasurementResult.Rejected(SourceTimingError(code, message).also(SourceTimingError::requireValid))

    /** Maps worker failure types to a bounded local recovery category. */
    private fun errorCode(type: String?): SourceTimingErrorCode = when (type) {
        "BadRequest" -> SourceTimingErrorCode.INVALID_REQUEST
        "WorkerError" -> SourceTimingErrorCode.WORKER_REJECTED
        else -> SourceTimingErrorCode.WORKER_REJECTED
    }
}

/** Worker-v2 timing payload; required timing fields are strict while unrelated analyze output is ignored. */
@Serializable
private data class WorkerTimingPayload(
    val analysisVersion: Int,
    val beats: List<WorkerTimingPoint> = emptyList(),
    val onsets: List<WorkerTimingPoint> = emptyList(),
    val tempoCandidates: List<TempoCandidate> = emptyList(),
    val leadingActivity: SourceTimingActivity? = null,
    val downbeat: DownbeatEvidence
) {
    /** Converts response-specific points into the domain's beat/onset representations. */
    fun toObservation(): SourceTimingObservation = SourceTimingObservation(
        workerContractVersion = analysisVersion,
        beats = beats.map(WorkerTimingPoint::asBeat),
        onsets = onsets.map(WorkerTimingPoint::asOnset),
        tempoCandidates = tempoCandidates,
        leadingActivity = leadingActivity,
        downbeat = downbeat
    ).also { observation ->
        SourceTimingEvidence(
            partId = "validation",
            source = InspectionSourceIdentity("source/validation.wav", "0".repeat(64)),
            workerContractVersion = observation.workerContractVersion,
            beats = observation.beats,
            onsets = observation.onsets,
            tempoCandidates = observation.tempoCandidates,
            leadingActivity = observation.leadingActivity,
            downbeat = observation.downbeat,
            groove = SourceGrooveTemplateDeriver.derive("0".repeat(64), observation.beats, observation.onsets)
        ).requireValid()
    }
}

/** Wire-level timing point that permits either a beat confidence or onset strength, never both. */
@Serializable
private data class WorkerTimingPoint(
    val frame: Int,
    val timeSeconds: Double,
    val confidence: Double? = null,
    val strength: Double? = null
) {
    /** Converts a worker point to a strictly confidence-scored beat. */
    fun asBeat() = SourceTimingPoint(frame, timeSeconds, confidence = requireNotNull(confidence), strength = null)

    /** Converts a worker point to a strictly strength-scored onset. */
    fun asOnset() = SourceTimingPoint(frame, timeSeconds, confidence = null, strength = requireNotNull(strength))
}
